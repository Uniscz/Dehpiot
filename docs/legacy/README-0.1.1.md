# Copiloto de Rentabilidade • Android 0.1.1

Aplicativo Android nativo para avaliar o lucro do ciclo completo de uma corrida, com OCR local, cartão sobreposto, histórico pessoal e **roteamento viário real integrado ao motor econômico**.

Esta entrega consolida a recuperação do projeto. O APK é uma compilação de teste assinada, instalável diretamente, sem publicação na Play Store. Identificador: `br.com.deh.copiloto`. Android mínimo: 8.0 (API 26); alvo e compilação: API 36.

## Instalar e começar

1. Transfira `Copiloto-0.1.1.apk` ao celular e abra o arquivo.
2. Se o Android pedir, autorize a instalação por esse navegador ou gerenciador de arquivos.
3. Abra **Copiloto**. Use **Testar demonstração** para explorar sem alterar histórico ou ganhos.
4. Em **Ajustes**, informe custos, consumo, mínimos de R$/hora e R$/km e meta. Toque em **Salvar configuração**.
5. Em **Regiões**, confirme um destino e a região de retorno por coordenadas, mapa ou GPS. Salve os pontos. Use **Calcular rota de carro** e **Usar rota no cálculo da corrida**.
6. Em **Hoje**, inicie o turno e a leitura. Autorize a sobreposição e a captura solicitadas pelo Android. Para leitura sobre outros apps, escolha o compartilhamento da tela apropriada na autorização do sistema.
7. O cartão mostra score, lucro por hora e por km. Arraste para mover; toque para expandir; pause ou pare pela notificação.
8. No **Histórico**, marque sua decisão e informe o resultado observado ao terminar o ciclo. Uma oferta observada ou aceita não entra como ganho realizado.

Também é possível preencher uma oferta manualmente ou importar um print em **Analisar**. Não são necessários login, chave de API ou cadastro em um serviço pago para os provedores públicos padrão.

Se outra compilação com o mesmo identificador e uma chave diferente estiver instalada, o Android pode recusar a atualização. Exporte seu histórico antes de desinstalar essa compilação. A chave de teste é gerada pelo ambiente de compilação; o projeto não inclui chaves privadas.

## Retorno de BR e rotas reais

`RoutingProvider` desacopla OSRM e Valhalla do domínio econômico. Ambos usam rede viária OpenStreetMap e devolvem distância, duração e geometria de uma rota de carro. Vias de mão única, restrições de conversão, acessos, alças, trevos e desvios são resolvidos pelo grafo do motor conforme os dados disponíveis.

O padrão é OSRM/FOSSGIS. **Evitar balsas** usa Valhalla, porque o servidor público OSRM escolhido não aceita a exclusão de balsas em sua configuração. É possível configurar outros servidores HTTPS compatíveis.

Exemplo exercitado nesta entrega: entre pontos próximos em Itajaí/Navegantes, OSRM retornou **7,1436 km / 17,6683 min** incluindo travessia; Valhalla sem balsa retornou **21,71 km / 34,0444 min**. Respostas reais estão em `app/src/androidTest/assets` e alimentam os testes. Não se trata de distância em linha reta multiplicada por um fator.

O retorno real entra em quilômetros totais, duração do ciclo, combustível/custo variável, custo fixo, R$/km, R$/hora, custo de oportunidade, Destination Score e score geral. O custo de oportunidade é uma comparação com o mínimo por hora; não é debitado outra vez do lucro líquido.

Em pista dividida, confirme o lado do desembarque e, quando conhecido, o sentido de saída. A interface aceita rumo de 0 a 359 graus. O motor limita a busca da via a 100 m; pontos afastados não são silenciosamente ligados a uma estrada distante.

**Destino sem coordenadas confirmadas:** a análise financeira básica continua disponível, com retorno explicitamente estimado e Destination Score pendente. O aplicativo não trata essa análise regional como concluída. Cadastre o ponto em Regiões. Em leituras posteriores, um nome de destino que corresponda exatamente a um ponto confirmado pode obter automaticamente a rota para a região de retorno salva.

## Compilar

Pré-requisitos: JDK 17, Android SDK Platform 36 e Build Tools 36.0.0. Abra esta pasta no Android Studio e sincronize o Gradle, ou configure `ANDROID_HOME` / um `local.properties` com `sdk.dir`.

Linux/macOS:

```sh
chmod +x gradlew
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

Windows:

```bat
gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`. A primeira compilação precisa de internet para baixar Gradle e dependências do Google Maven e Maven Central. O wrapper, seu JAR e o SHA-256 da distribuição Gradle estão incluídos. Não há dependência de caminhos deste ambiente de recuperação.

Testes no aparelho/emulador:

```sh
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w br.com.deh.copiloto.test/androidx.test.runner.AndroidJUnitRunner
```

O teste de interface pressupõe uma instalação sem configurações e histórico anteriores. Use um emulador limpo para não apagar dados pessoais. Para reproduzir a prova de OCR offline, desative Wi-Fi e dados móveis antes da instrumentação; as respostas de rotas são fixtures locais de consultas reais.

Assinatura release opcional: defina `COPILOTO_KEYSTORE`, `COPILOTO_STORE_PASSWORD`, `COPILOTO_KEY_ALIAS` e `COPILOTO_KEY_PASSWORD`, depois execute `./gradlew :app:assembleRelease`. Segredos não devem ser incluídos no repositório. O APK entregue usa a assinatura de teste; não exige esses parâmetros para instalar.

## Organização

| Caminho | Conteúdo |
|---|---|
| `core` | Economia, custos, score, contrato de rotas, parser e aprendizado pessoal sem dependência de Android |
| `app/.../routing` | Provedores OSRM/Valhalla, configuração, cache e normalização de respostas |
| `app/.../capture` | OCR ML Kit embarcado, MediaProjection, serviço visível e overlay |
| `app/.../data` | Room, DataStore, snapshots econômicos, resultados observados e CSV |
| `app/.../ui` | Compose, mapa MapLibre, rotas, análise, histórico, ajustes e dashboard |
| `docs` | Arquitetura, privacidade, decisões de roteamento, estado funcional e validação |
| `docs/evidencias` | Resultados dos testes e verificações desta compilação |

Consulte `docs/VALIDACAO.md` para os resultados efetivamente executados e `docs/ESTADO.md` para o alcance desta versão. A contagem histórica de 41 testes da compilação perdida não é usada como validação do APK entregue.
