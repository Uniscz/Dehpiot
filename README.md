# Dehpilot 0.2.0 — revisão em desenvolvimento

**Código modificado e preservado. NÃO compilado, NÃO validado em Android e SEM APK novo nesta entrega.** O ambiente desta revisão não tinha Android SDK/Gradle instalado; o download do wrapper falhou com `Network is unreachable`. Os resultados históricos da 0.1.1 não validam a 0.2.0.

Aplicativo Android nativo de rentabilidade pessoal. Esta revisão parte integralmente do Copiloto 0.1.1 enviado pelo usuário. Mantém Kotlin, Compose, ML Kit local, Room, DataStore, OSRM, Valhalla, MapLibre, MediaProjection, overlay, CSV, testes anteriores e motor econômico. Não houve reconstrução do zero.

## O que mudou no código

- Marca Dehpilot, monograma próprio, paleta sóbria, hierarquia de valores, navegação Hoje / Mapa / Histórico / Análises / Mais.
- Pilha de navegação com AndroidX PredictiveBackHandler, estado salvo, retorno interno e rascunhos. Formulários de corrida preservam entrada ao voltar.
- Home com líquido/hora online, meta bruta/líquida, ritmo, prazo opcional, ETA aproximada, tendência observada, produtividade e km vazio.
- Modo direção com análise recente, score legível, mínimos e risco; expira resultados. Overlay preservado e com menos reconstruções repetidas.
- Foto ou galeria para consumo do painel; OCR local, parser específico, unidades km/L e L/100 km, escolha explícita de candidato, correção manual. Nenhuma leitura é aplicada automaticamente.
- Combustível por litro/consumo alimenta o motor. Custos detalhados continuam disponíveis. Configurações antigas de custo manual/km não são reinterpretadas silenciosamente.
- Histórico de alterações de custos; snapshots por oferta preservados; custo fixo registrado no início dos novos turnos.
- Histórico em lista virtualizada, filtros, detalhes previsto × real, decisão manual e conclusão transacional. Exportação CSV lê todo o histórico armazenado, não apenas as linhas visíveis.
- Análises diárias/7 dias/30 dias, períodos anteriores, gráficos, destinos/horários com amostra mínima e medição de erro da previsão.
- Mapa pessoal com pontos confirmados e cores baseadas em resultados observados. Casa definida separadamente; modo retorno muda o ponto real do roteamento e permite restaurar região de trabalho.
- Onboarding em etapas, explicação de permissões, indicação de conexão, diagnóstico OCR secundário e feedback de ações.
- Correção de parser para valor total e tarifa/km na mesma linha, caso do print enviado.

Implementação em fonte não equivale a funcionalidade aprovada. Leia `docs/AUDITORIA-DEHPILOT.md`, `docs/ESTADO.md` e `docs/VALIDACAO.md`.

## Compilar e testar em ambiente Android

Requer JDK 17, Android SDK Platform 36, Build Tools 36.0.0, internet para dependências e emulador/aparelho para instrumentação. Wrapper Gradle 8.11.1 incluído; checksums originais preservados.

```sh
chmod +x gradlew scripts/verify.sh
./scripts/verify.sh
# Com emulador descartável conectado:
./scripts/verify.sh --device
```

Equivalente manual:

```sh
./gradlew :core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

**Os testes UI apagam dados no dispositivo de teste. Use emulador descartável.**

O APK, se a compilação passar, estará em `app/build/outputs/apk/debug/app-debug.apk`. O script copia uma versão para `verification/Dehpilot-0.2.0-debug.apk` e calcula SHA-256. Nenhum desses APKs foi gerado aqui. Capture screenshots reais e percorra as jornadas de `docs/JORNADAS-QA.md` antes de aprovar a interface.

## Compatibilidade com a instalação anterior

O applicationId permanece **br.com.deh.copiloto**, deliberadamente, para preservar a identidade Android e os dados. O nome exibido é **Dehpilot**. Banco continua `copiloto.db`; Room migra da versão 1 para 2 acrescentando somente o snapshot de custo fixo do turno. Não há migração destrutiva.

A chave de assinatura da compilação anterior **não veio no ZIP**. Uma nova chave debug não garante atualização sobre o APK antigo. Para uma atualização que preserve dados, compilar com a mesma chave. Não orientar desinstalação antes de preservar os registros; CSV é exportação legível, não backup restaurável completo.

Variáveis de assinatura release originais continuam aceitas: `COPILOTO_KEYSTORE`, `COPILOTO_STORE_PASSWORD`, `COPILOTO_KEY_ALIAS`, `COPILOTO_KEY_PASSWORD`. Não incluir segredos no projeto.

## Privacidade e limites

Sem login, anúncios, automação de aceitação ou controle de Uber/99. OCR e registros locais; servidores de rotas recebem coordenadas e o mapa solicita tiles. Câmera usa o aplicativo de câmera do aparelho e arquivo temporário via FileProvider; a imagem é reduzida antes do OCR e respeita orientação EXIF. A captura contínua só existe após autorização do Android e mantém controles de pausa/parada.

Sem trânsito ao vivo, preço automático de pedágio, demanda global ou garantia de oportunidade. Regiões desconhecidas continuam incertas. Estados de trabalho são informados pelo motorista, não detecção automática de passageiro. Resultados dependem dos registros reais. Casa como destino reduz custo de retorno quando a rota efetivamente o permite; não há bônus artificial no score.

## Organização

| Arquivo/módulo | Responsabilidade |
|---|---|
| `core/Economics.kt`, `Routing.kt` | Motor econômico e contrato de rotas preservados |
| `core/Product.kt` | Parser de painel, combustível, navegação, metas, métricas e calibração |
| `ui/ProductShell.kt` | Identidade, navegação, Home e meta |
| `ui/ProductFlows.kt` | Consumo, direção, onboarding, permissões e diagnóstico |
| `ui/Analytics.kt` | Análises e mapa pessoal |
| `ui/Screens.kt` | Simulador, histórico, resultado real e ajustes |
| `capture` | OCR embarcado, fotos, serviço e overlay |
| `data/Storage.kt` | Persistência, snapshots e migração 1→2 |
| `routing/Providers.kt` | OSRM/Valhalla, cache e retorno para casa |
| `docs/legacy` | Documentação original, sem atribuir seus testes à revisão |

A especificação original de cerca de 40 páginas não foi localizada. A auditoria compara a fonte 0.1.1, sua documentação e a solicitação Dehpilot disponível nesta revisão; não declara comparação com documento não lido.

## Compilação remota preparada

`.github/workflows/android.yml` prepara SDK 36 e JDK 17, executa testes JVM/lint/build, preserva o primeiro APK compilado, executa testes Android em emulador descartável API 35 e guarda relatórios mesmo em falhas. O artefato `compiled-pending-device-tests` não representa aprovação dos testes Android; `automated-checks-passed` exige todos os passos anteriores aprovados. QA visual e jornadas continuam necessários. A rotina foi escrita, mas ainda não executada: nenhum repositório deste projeto estava acessível nesta sessão.

A assinatura debug de um runner novo pode mudar entre execuções. Estes APKs são para validação; não substituir uma instalação com dados importantes sem conferir certificado e estratégia de backup. A assinatura definitiva deve usar a chave original, não incluída na base enviada.

Referências de configuração: https://github.com/android-actions/setup-android e https://github.com/ReactiveCircus/android-emulator-runner.
