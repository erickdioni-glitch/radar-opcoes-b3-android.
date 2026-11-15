# Radar de Opções B3 PRO — Android 3.1 (scanner corrigido)

## Correção de 15/11/2026
- Parser corrigido para o formato da fonte que usa as colunas `ticker`, `tipo`, `strike`, `ultimo`, `numerodenegocios` e `data/hora`.
- Quando Yahoo não responde (HTTP 429 observado), cotação de referência estimada pela própria grade de opções e **marcada como indicativa**, não como preço executável.
- Contratos de estilo não identificado aparecem como `?` e podem entrar em **simulações indicativas**, sem ordem de execução. Verificar estilo de exercício, lotes e BID/ASK na corretora.
- Foram verificadas 293 séries na resposta pública de PETR4 para vencimento de referência de 19/11/2026, mas isso não valida todos os ativos nem o funcionamento em todos os aparelhos.

## Conteúdo
- Aplicativo usa diretamente a interface PRO embutida: Radar, Estruturas e Montador manual.
- Ao abrir, consulta fontes públicas (Opcoes.net.br para cadeia EOD, Yahoo Finance para preço do ativo) por HTTPS nativo, passando dados ao motor de estratégias do PRO.
- Atualiza ao entrar e a cada 10 minutos enquanto a tela está visível. Dados podem estar atrasados, incompletos ou indisponíveis; fontes gratuitas podem limitar acesso.
- Não inventa cotações: sem dados sincronizados, não gera operações automáticas; o simulador manual continua disponível.
- Não negocia, não recomenda uma ordem e não garante retorno. Confirmar na corretora bid/ask, lote, vencimento, liquidez, garantia, custos e exercício.

## Instalação
Faça download do APK puro na release v3.1.0. Esta versão tem applicationId próprio (`br.com.radaropcoes.b3proscan`) e não substitui as versões antigas.

## Construção
GitHub Actions: Gerar APK Android, artefato Radar-Opcoes-B3-APK. Android >= 7.0. Código de fontes: app/src/main/java/br/com/radaropcoes/b3/B3RadarData.java.
