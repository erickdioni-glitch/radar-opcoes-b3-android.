package br.com.radaropcoes.b3;

import android.webkit.WebResourceResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Quotes are retrieved from external public services, never generated locally.
 * A failed source yields an explicit error, not fabricated values.
 */
public final class B3RadarData {
    private B3RadarData() { }

    private static final String[] TICKERS = {
        "PETR4", "VALE3", "ITUB4", "BBAS3", "BOVA11", "BBDC4", "PRIO3", "B3SA3"
    };
    private static final String BASE = "https://opcoes.net.br/listaopcoes/completa";
    private static final Pattern OPTION_CODE = Pattern.compile("^([A-Z0-9]{4})([A-X])\\d+(W\\d+)?_(20\\d\\d)$");
    private static long nextRequest = 0L;

    static WebResourceResponse handleRequest() {
        try {
            JSONObject response = build();
            return jsonResponse(200, response.toString());
        } catch (Exception error) {
            return jsonResponse(503, "{\"error\":" + JSONObject.quote("Não foi possível consultar cotações públicas: " + message(error)) + "}");
        }
    }

    private static WebResourceResponse jsonResponse(int code, String value) {
        Map<String,String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store, max-age=0");
        headers.put("Access-Control-Allow-Origin", "*");
        return new WebResourceResponse(
            "application/json", "UTF-8", code,
            code == 200 ? "OK" : "Service Unavailable", headers,
            new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static JSONObject build() throws Exception {
        JSONArray good = new JSONArray();
        JSONArray failed = new JSONArray();
        for (String ticker: TICKERS) {
            try {
                JSONObject item = getTicker(ticker);
                good.put(item);
            } catch (Exception e) {
                failed.put(new JSONObject().put("ticker", ticker).put("error", message(e)));
            }
        }
        if (good.length() == 0) {
            throw new Exception("Nenhum ativo pôde ser consultado; falhas: " + failed.toString().substring(0, Math.min(330, failed.toString().length())));
        }
        return new JSONObject()
            .put("generatedAt", isoDateTime())
            .put("data", good)
            .put("failed", failed)
            .put("source", "opcoes.net.br / Yahoo Finance")
            .put("mode", "cotacoes-publicas-indicativas");
    }

    private static JSONObject getTicker(String ticker) throws Exception {
        JSONObject datesResponse = getJson(BASE + "?idAcao=" + ticker + "&listarVencimentos=true&cotacoes=false");
        JSONArray expiries = datesResponse.getJSONObject("data").optJSONArray("vencimentos");
        if (expiries == null || expiries.length() == 0) throw new Exception("Sem calendário de vencimentos");
        JSONArray selected = new JSONArray();
        StringBuilder selectedDates = new StringBuilder();
        for (int i = 0; i < expiries.length() && selected.length() < 3; i++) {
            JSONObject exp = expiries.optJSONObject(i);
            if (exp == null) continue;
            JSONObject attr = exp.optJSONObject("dataAttributes");
            if (attr == null || !"1".equals(attr.optString("m"))) continue;
            int days = numberAsInt(attr.opt("du"));
            if (days < 2 || days > 130) continue;
            String date = exp.optString("value", "");
            if (!date.matches("\\d{4}-\\d\\d-\\d\\d")) continue;
            selected.put(new JSONObject().put("date", date).put("days", days).put("week", ""));
            if (selectedDates.length() > 0) selectedDates.append(",");
            selectedDates.append(date);
        }
        if (selected.length() == 0) throw new Exception("Sem vencimentos mensais disponíveis");

        JSONObject chainResponse = getJson(BASE + "?idAcao=" + ticker +
            "&listarVencimentos=true&cotacoes=true&vencimentos=" + selectedDates);
        JSONObject chainData = chainResponse.getJSONObject("data");
        JSONArray rows = chainData.optJSONArray("cotacoesOpcoes");
        if (rows == null || rows.length() == 0) throw new Exception("Sem opções retornadas pela fonte");

        // The chain may provide a corrected per-ticker calendar; use it when present.
        JSONArray ownCalendar = chainData.optJSONArray("vencimentos");
        if (ownCalendar != null && ownCalendar.length() > 0) {
            selected = new JSONArray();
            for (int i = 0; i < ownCalendar.length(); i++) {
                JSONObject exp = ownCalendar.optJSONObject(i);
                if (exp == null) continue;
                JSONObject attr = exp.optJSONObject("dataAttributes");
                String date = exp.optString("value","");
                if (attr == null || selectedDates.indexOf(date) < 0 || !"1".equals(attr.optString("m"))) continue;
                selected.put(new JSONObject()
                    .put("date",date).put("days",numberAsInt(attr.opt("du")))
                    .put("week",attr.optString("w","")));
            }
        }

        // A fonte alterou a estrutura das linhas. Resolver pelos NOMES de colunas,
        // pois os antigos índices confundiam strike, último, negócios e idAcao.
        JSONArray columns = chainData.optJSONArray("columns");
        int ixSymbol = columnIndex(columns, "ticker", 0);
        int ixKind = columnIndex(columns, "tipo", 2);
        int ixStrike = columnIndex(columns, "strike", 3);
        int ixLast = columnIndex(columns, "ultimo", 5);
        int ixDistance = columnIndex(columns, "distancia%dostrike", 4);
        int ixTrades = columnIndex(columns, "numerodenegocios", 6);
        int ixLastDate = columnIndex(columns, "data/hora", 7);
        int ixStyle = columnIndex(columns, "modalidade", -1); // Normalmente não existe.
        JSONArray options = new JSONArray();
        ArrayList<Double> spotCandidates = new ArrayList<>();
        String latestOptionDate = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONArray raw = rows.optJSONArray(i);
            if (raw == null || raw.length() < 8) continue;
            double strike = number(raw.opt(ixStrike));
            double last = number(raw.opt(ixLast));
            double distance = number(raw.opt(ixDistance));
            if (strike > 0 && Double.isFinite(distance) && distance > -0.98 && distance < 50) {
                double implied = strike / (1.0 + distance);
                if (implied > 0 && implied < 100000 && Double.isFinite(implied)) spotCandidates.add(implied);
            }
            if (!(strike > 0 && last > 0)) continue;
            String rawSymbol = raw.optString(ixSymbol, "");
            JSONObject expiry = expiryOf(rawSymbol, selected);
            if (expiry == null || expiry.optInt("days") <= 1) continue;
            String lastDate = raw.optString(ixLastDate, "");
            lastDate = lastDate.length() >= 10 ? lastDate.substring(0, 10) : "";
            if (lastDate.matches("\\d{4}-\\d\\d-\\d\\d") &&
                (latestOptionDate == null || lastDate.compareTo(latestOptionDate) > 0)) {
                latestOptionDate = lastDate;
            }
            String symbol = rawSymbol.split("_")[0];
            String style = ixStyle >= 0 ? raw.optString(ixStyle, "?") : "?";
            if (!"A".equals(style) && !"E".equals(style)) style = "?";
            JSONObject opt = new JSONObject()
                .put("symbol", symbol)
                .put("kind", "PUT".equalsIgnoreCase(raw.optString(ixKind)) ? "PUT" : "CALL")
                .put("style", style)
                .put("strike", strike)
                .put("last", last)
                .put("bid", JSONObject.NULL)
                .put("ask", JSONObject.NULL)
                .put("trades", numberAsInt(raw.opt(ixTrades)))
                .put("volume", 0) // Fonte atual não fornece volume no layout consultado.
                .put("lastDate", lastDate)
                .put("expiry", expiry.optString("date"))
                .put("days", expiry.optInt("days"))
                .put("contractSize", JSONObject.NULL);
            options.put(opt);
        }
        if (options.length() == 0) throw new Exception("Nenhuma série compatível retornada");
        Collections.sort(spotCandidates);
        double impliedSpot = spotCandidates.isEmpty() ? 0 : spotCandidates.get(spotCandidates.size()/2);
        double spot = 0;
        String spotDate = null;
        String spotSource = "Cotação estimada da grade; data não confirmada";
        JSONArray warnings = new JSONArray();

        try {
            JSONObject yahoo = getJson("https://query1.finance.yahoo.com/v8/finance/chart/" +
                ticker + ".SA?range=1mo&interval=1d");
            JSONArray series = yahoo.getJSONObject("chart").getJSONArray("result");
            JSONObject meta = series.getJSONObject(0).getJSONObject("meta");
            spot = meta.optDouble("regularMarketPrice", 0);
            long unix = meta.optLong("regularMarketTime",0);
            if (unix > 0 && spot > 0) {
                spotDate = saoPauloDate(unix * 1000L);
                spotSource = "Yahoo Finance (cotação pública, possivelmente atrasada)";
            } else spot = 0;
        } catch (Exception e) {
            warnings.put("A cotação da ação via Yahoo Finance está indisponível; valores estimados da grade não validam estratégias.");
        }
        // Yahoo pode responder 429; a própria grade permite inferir a cotação
        // aproximada a partir de strike e distância. Não é uma cotação executável.
        if ((spot <= 0 || spotDate == null) && impliedSpot > 0 && latestOptionDate != null) {
            spot = impliedSpot;
            spotDate = latestOptionDate;
            spotSource = "Estimativa calculada da grade de opções (não é cotação confirmada)";
            warnings.put("Preço do ativo inferido da distância dos strikes na grade. Data atribuída ao último negócio de opção; NÃO é confirmação de cotação da ação nem de horário sincronizado.");
        }
        if (spot <= 0) throw new Exception("Sem cotação da ação e sem estimativa da grade");
        if (spotDate == null) {
            warnings.put("Sem data verificável da ação: estratégias automáticas indisponíveis.");
        } else if (latestOptionDate != null && !spotDate.equals(latestOptionDate)) {
            warnings.put("Datas da ação e das opções são diferentes; o motor exclui séries não sincronizadas.");
        }
        warnings.put("Estilo de exercício, BID/ASK, quantidade do contrato e custos precisam de confirmação na corretora antes de operar.");

        int matching = 0;
        for (int i=0;i<options.length();i++) {
            JSONObject o = options.getJSONObject(i);
            if (spotDate != null && spotDate.equals(o.optString("lastDate")) && o.optInt("trades") >= 5) matching++;
        }
        return new JSONObject()
            .put("ticker",ticker)
            .put("spot",spot)
            .put("spotDate",spotDate == null ? JSONObject.NULL : spotDate)
            .put("optionDate",latestOptionDate == null ? JSONObject.NULL : latestOptionDate)
            .put("source",spotSource)
            .put("optionsSource","opcoes.net.br (últimos negócios, EOD)")
            .put("synchronizedCount",matching)
            .put("warnings",warnings)
            .put("options",options);
    }

    private static int columnIndex(JSONArray columns, String name, int fallback) {
        if (columns == null) return fallback;
        for (int i = 0; i < columns.length(); i++) {
            JSONObject col = columns.optJSONObject(i);
            if (col != null && name.equalsIgnoreCase(col.optString("name", ""))) {
                return col.optInt("index", i);
            }
        }
        return fallback;
    }

    private static JSONObject expiryOf(String code, JSONArray selected) {
        Matcher m = OPTION_CODE.matcher(code);
        if (!m.matches()) return null;
        String ch = m.group(2);
        String months = "ABCDEFGHIJKL";
        String puts = "MNOPQRSTUVWX";
        int idx = months.indexOf(ch);
        if (idx < 0) idx = puts.indexOf(ch);
        if (idx < 0) return null;
        String ym = m.group(4) + "-" + String.format(Locale.US, "%02d", idx + 1);
        String weekly = m.group(3);
        for (int i = 0; i < selected.length(); i++) {
            JSONObject e = selected.optJSONObject(i);
            if (e == null || !e.optString("date").startsWith(ym)) continue;
            if (weekly == null || weekly.isEmpty()) return e;
            if (weekly.equals(e.optString("week"))) return e;
        }
        return null;
    }

    private static synchronized void throttle(String url) throws InterruptedException {
        if (!url.startsWith(BASE)) return;
        long now = System.currentTimeMillis();
        long delay = Math.max(0L, nextRequest - now);
        nextRequest = Math.max(now, nextRequest) + 950L;
        if (delay > 0) Thread.sleep(delay);
    }

    private static JSONObject getJson(String address) throws Exception {
        Exception last = null;
        for (int i=0;i<2;i++) {
            HttpURLConnection conn = null;
            try {
                throttle(address);
                conn = (HttpURLConnection)new URL(address).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(9000);
                conn.setReadTimeout(13000);
                conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 Chrome/130.0 Safari/537.36");
                conn.setRequestProperty("Accept","application/json, text/plain, */*");
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);
                try (InputStream input=conn.getInputStream(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n=input.read(buffer))>=0) {
                        output.write(buffer,0,n);
                        if (output.size()>8_000_000) throw new Exception("Resposta grande demais");
                    }
                    return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                last=e;
            } finally {
                if (conn!=null) conn.disconnect();
            }
        }
        throw new Exception(new URL(address).getHost() + ": " + message(last));
    }

    private static double number(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) return Double.NaN;
        try {
            return Double.parseDouble(String.valueOf(value).replace(",","."));
        } catch (Exception ignored) { return Double.NaN; }
    }

    private static int numberAsInt(Object o) {
        double value=number(o);
        return Double.isFinite(value) ? (int)value : 0;
    }

    private static String saoPauloDate(long millis) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        return fmt.format(new Date(millis));
    }

    private static String isoDateTime() {
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }

    private static String message(Exception e) {
        if (e == null) return "Erro de conexão";
        String msg=e.getMessage();
        return msg == null || msg.length()==0 ? e.getClass().getSimpleName() : msg.substring(0,Math.min(msg.length(),150));
    }
}
