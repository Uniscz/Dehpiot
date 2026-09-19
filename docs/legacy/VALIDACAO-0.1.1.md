# Validação da entrega 0.1.1

## Resultado efetivo

| Verificação | Resultado nesta recuperação |
|---|---|
| Compilação `:app:assembleDebug` | Aprovada; APK produzido |
| Testes JVM `:core:test` | **29 testes, 0 falhas, 0 erros, 0 ignorados** |
| Android lint `:app:lintDebug` | **0 erros e 7 avisos não bloqueantes** |
| Compilação `:app:assembleDebugAndroidTest` | Aprovada; 10 testes Android disponíveis no código |
| Execução desses 10 testes Android | **Não concluída nesta recuperação** |
| Consultas online reais OSRM e Valhalla | Ambas responderam com rotas; respostas preservadas nos assets de teste |
| Integridade e assinatura do APK final | Verificadas novamente após recuperar o arquivo persistido: assinatura V2 válida, um signatário, nenhum erro |
| Instalação e abertura no emulador nesta recuperação | Não concluídas: o emulador respondeu `Can't find service: package` |
| Instalação no celular do usuário | Não executada por este agente |

O APK é um pacote Android compilado e assinado para instalação direta, não um protótipo web ou arquivo renomeado. A verificação de assinatura não substitui a validação de interface e de captura contínua no aparelho.

## O que os 29 testes executados cobrem

- Custo e duração de todas as etapas do ciclo, incluindo extras e custo fixo.
- Regressão **2 km próximos / 9 km viários**: usa 9 km no retorno; modifica combustível/custo variável, R$/km, R$/hora, oportunidade, Destination Score e score geral.
- Preservação da origem da rota e impedimento de Destination Score concluído sem rota.
- Cenários de espera sem variar uma distância viária já calculada.
- Consumo urbano/rodoviário ponderado em litros, números brasileiros e arredondamento monetário.
- Âncoras de score, estratégias e lucro negativo.
- Rejeição de tempo zero, NaN, consumo zero, rumo e coordenadas inválidos.
- Detecção de acesso difícil, inclusive ponto geograficamente quase coincidente com longo percurso.
- Parser: cinco campos, leitura incompleta, tarifa ambígua, bônus/saldo, metros/horas, ordem incerta, nomes pessoais e junção espacial de linhas.
- Aprendizado com mínimo de amostras, mediana/suavização, isolamento por região e preservação da rota real.

Arquivo de teste: `core/src/test/kotlin/br/com/deh/copiloto/core/CoreTest.kt`.

O XML inspecionado nesta sessão registrou `tests=29`, `failures=0`, `errors=0`, `skipped=0`, duração de execução de testes `0.152` s e timestamp `2026-09-13T03:22:29`. A compilação final combinada terminou com `BUILD SUCCESSFUL in 1m 36s`, 77 tarefas, das quais 15 executadas e 62 atualizadas. O núcleo não mudou entre a execução dos 29 testes e o APK final; na compilação final, a tarefa `:core:test` ficou `UP-TO-DATE`.

O último ajuste do APK protege a imagem importada contra liberação durante OCR nativo/desenho de tela e descarta leituras obsoletas quando o usuário inicia outra análise.

## Testes Android disponíveis, porém não executados no APK recuperado

Nove casos em `IntegrationTest`: OCR embarcado sobre uma imagem sintética; decodificação da resposta real OSRM com balsa; resposta real Valhalla sem balsa aplicada à economia; indisponibilidade sem fallback geográfico; capacidades de tráfego/pedágios; chave de cache com direção/rumo/provedor; erro NoSegment; serialização de configurações; conclusão transacional sem duplicação ou enriquecimento tardio de corrida concluída.

Um caso em `UiSmokeTest`: demonstração isolada, acesso à tela de roteamento e histórico vazio. Os dez casos foram **compilados**, não contabilizados como aprovados. Não há afirmação de OCR offline revalidado por execução Android nesta entrega.

O emulador Android 14/API 34 com execução por software não manteve o serviço de pacotes disponível. A tentativa de instalação retornou `adb: failed to install ...: cmd: Can't find service: package`. Essa falha ocorreu no ambiente de teste antes da instalação, não é um diagnóstico de falha do aplicativo. Para não adiar novamente a entrega, o APK e o código foram preservados e essa cobertura pendente é declarada.

## Evidência de roteamento real

Consulta A: latitude -26.900067, longitude -48.691499. Consulta B: latitude -26.906382, longitude -48.648100.

| Provedor/opção | Distância viária | Duração retornada |
|---|---:|---:|
| OSRM, aproximação pela calçada e raio de 100 m | 7.1436 km | 1060.1 s |
| Valhalla, automobile e `exclude_ferries=true` | 21.71 km | 2042.662 s |

OSRM incluiu travessia de balsa. Valhalla indicou rodovia e ausência de balsa. As respostas brutas estão em `app/src/androidTest/assets/osrm-live.json` e `valhalla-live.json`. O caso 2/9 km do núcleo é uma regressão sintética do requisito, distinta dessas consultas reais.

## Avisos do lint

Cinco sugestões de extensões KTX, uma recomendação de posição do parâmetro `Modifier` e um aviso `ImplicitSamInstance` na chamada de `stopService(Intent)`. O último foi inspecionado: o serviço é selecionado pelo componente explícito do Intent, não pela identidade de um listener. Nenhum erro de API mínima, manifesto ou permissão foi reportado pelo lint.

## Histórico de validação anterior

A conversa registrava 41 testes aprovados e instalação da versão 0.1.0 antes da perda daquele ambiente. **Esses resultados não validam automaticamente a 0.1.1 e não foram somados à contagem desta entrega.** Os logs originais dessa compilação não foram recuperados.

Outra manutenção ocorreu durante o fechamento. O APK final e o código completo foram recuperados dos checkpoints já salvos, evitando nova reconstrução. Este relatório consolida as saídas verificadas na conversa; não apresenta logs recriados como se fossem os arquivos originais.

## Identidade do APK final

- Arquivo: `Copiloto-0.1.1.apk`
- Pacote: `br.com.deh.copiloto`
- Versão: 0.1.1, código 2
- Android mínimo: 8.0/API 26
- Tamanho: 106490981 bytes
- SHA-256: `5d5b40c95f015595dccc8b233237a77d4e2ba8dd66d14de0afcdf4edcf7f53fc`
- Verificador oficial Android `apksig` 8.10.1, plataforma mínima de verificação 26: `verified=true`, `v2=true`, `signers=1`, `errors=[]`.

Leia `ESTADO.md` para as funcionalidades e limitações. Instruções de instalação, compilação e repetição dos testes estão no README.
