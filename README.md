# Radar de Opções B3 — Android (versão online)

APK Android que abre em tela cheia o Radar de Opções B3 hospedado em
https://radar-opcoes-b3.lovable.app/ .

## Varredura automática
- Ao abrir, o app carrega o Radar web hospedado e dispara a análise de mercado do seu backend TanStack Start.
- Enquanto a tela inicial está aberta, o APK recarrega a cada 10 minutos; ao voltar depois de mais de 10 minutos, também atualiza.
- As cotações e estratégias dependem da disponibilidade do site publicado e das fontes de dados que ele consulta.
- Sem conexão ao servidor, cai para a interface manual/offline incluída no APK. Nesse modo, NÃO há cotações automáticas e NÃO são inventados preços.
- O aplicativo não envia ordens nem garante lucro; verifique fonte, referência temporal, BID/ASK, liquidez, exercício, custos e garantias na corretora.

## Instalar
No GitHub, abra Actions > **Gerar APK Android**, baixe o artifact **Radar-Opcoes-B3-APK** e extraia o **app-debug.apk** do ZIP. Por ser assinatura de depuração automática, se uma versão anterior já estiver instalada e ocorrer erro de assinatura, desinstale a anterior antes de instalar a nova (isso apaga dados salvos nela).

## Tecnologia
Android Java + WebView com HTTPS + fallback via WebViewAssetLoader. Android 7+.
