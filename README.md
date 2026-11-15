# Radar de Opções B3 — Aplicativo Android

Aplicativo Android que empacota a interface do **Radar de Opções B3 Pro — Estratégias Estruturadas** para abrir no celular, em tela cheia, sem depender da instalação de uma PWA no navegador.

## O que funciona

- Navegação pelas abas Radar, Estruturas e Montador Manual;
- Simulações manuais de CALL/PUT, compras e vendas, custos e cenários;
- Interface local embutida no APK e disponível sem internet;
- Suporte a consumo do endpoint `/api/radar` quando houver um backend HTTPS configurado.

## Atenção: consultas online

A interface original fazia a consulta de mercado com `fetch('/api/radar')`. A implementação do servidor não está incluída no HTML, portanto **o APK ainda não consegue obter cotações automaticamente**.

Sem backend, o aplicativo exibe aviso na aba Radar, e o montador manual permanece disponível. Não são gerados preços fictícios. Para ativar a varredura online:

1. Hospedar um servidor com um endpoint HTTPS que retorne o mesmo JSON usado pelo Radar original.
2. Ajustar a constante `RADAR_API_URL` em `app/src/main/java/br/com/radaropcoes/b3/MainActivity.java`.
3. Executar novamente a compilação no GitHub Actions.

As cotações eventualmente exibidas podem ter atraso; a ferramenta não executa ordens. Confirme preços BID/ASK, liquidez, multiplicador, custos e riscos na corretora antes de operar.

## Gerar o APK sem computador

1. Abra **Actions** neste repositório no GitHub.
2. Entre no fluxo **Gerar APK Android**.
3. Clique em **Run workflow** > **Run workflow**.
4. Após a execução terminar com sucesso, abra a execução.
5. Baixe o artifact **Radar-Opcoes-B3-APK** (um arquivo ZIP).
6. Extraia o ZIP no celular para obter `app-debug.apk` e instale o arquivo.

O APK de debug é assinado automaticamente para testes. Não é um APK de distribuição pela Play Store. Para atualizações estáveis, recomenda-se gerar e armazenar uma chave de assinatura própria.

## Tecnologia

Android Java + WebViewAssetLoader (AndroidX WebKit), Gradle e GitHub Actions. O projeto exige Android 7.0 ou superior.
