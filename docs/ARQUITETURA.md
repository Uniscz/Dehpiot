# Adendo da revisão Dehpilot 0.2.0

A base descrita abaixo foi preservada. Novos módulos `Product.kt`, `ProductShell.kt`, `ProductFlows.kt`, `Analytics.kt`, `Connectivity.kt` e `PhotoDecoder.kt` acrescentam a camada de produto.

A navegação usa a infraestrutura AndroidX Activity já presente, com pilha determinística no ViewModel, SavedStateHandle e SaveableStateHolder. Gesto concluído faz pop; gesto cancelado não. Referência oficial: https://developer.android.com/develop/ui/compose/system/predictive-back-progress . A execução Android permanece pendente.

Persistência: Room 2, migração aditiva de `sessions.fixedPerHour`; parâmetros de combustível/retorno em codecs compatíveis; snapshots econômicos antigos não são reescritos. O arquivo `app/schemas/.../2.json` deve ser produzido pelo KSP na primeira compilação bem-sucedida e incluído no próximo checkpoint.

`fuelBasedSimple=false` em custos antigos conserva o valor manual/km. Novas confirmações de combustível ativam cálculo por preço/consumo. Atualizar painel no modo detalhado altera consumo urbano e mantém consumo rodoviário específico existente.

Relatório de período usa custo fixo dos turnos quando todos os turnos envolvidos têm snapshot. Se faltam snapshots, usa custos de ciclos gravados. Não soma novamente os custos fixos das corridas ao tempo de turno. Dados reais continuam informados pelo usuário.

---

## Documento original da base 0.1.1

# Arquitetura consolidada

O domínio Kotlin/JVM está em `core`, independente de Android, rede, mapa ou fornecedor. `Economics.evaluate` recebe uma oferta, premissas, custos e política; devolve uma previsão imutável. Valores monetários são arredondados em centavos; tempo em minutos e distância em km.

## Roteamento e economia

O contrato `RoutingProvider` inclui `calculateRoute`, `getDrivingDistance`, `getEstimatedDuration`, `supportsTraffic`, `supportsTolls`, `getTollEstimate` e `getRouteGeometry`. `DrivingRoute` guarda a consulta, geometria, distância, duração, provedor, momento de cálculo, manobras, referências de vias e capacidades utilizadas.

`OsrmRoutingProvider` e `ValhallaRoutingProvider` são duas implementações funcionais de `FreeRoutingProvider`. Não há classe premium vazia simulando integração. Uma implementação Google, HERE ou TomTom poderá normalizar a resposta para o mesmo contrato e ser injetada pelo repositório de rotas, sem alterar o motor econômico. Tráfego e preços de pedágios são capacidades explícitas; os provedores atuais não anunciam essas capacidades.

`RouteEconomics.applyReposition` transfere os quilômetros e minutos efetivos para o ciclo econômico e mantém sua origem. A diferença entre proximidade e condução ativa o aviso de acesso difícil se houver pelo menos 1 km extra e razão de acesso >= 1,7. Esse limite é uma heurística de alerta, não prova isolada da existência de um retorno de BR específico. A própria rota é o dado usado no custo. Nenhuma tabela manual de retornos é necessária.

Não há fallback de Haversine para distância de carro. Sem rota ou cache válido, `RoutingResult.Unavailable` informa o motivo. Campos manuais são identificados como estimativas e não produzem um Destination Score concluído.

## Modelo econômico

- Km = busca + viagem + retorno/reposicionamento + desvio adicional.
- Minutos = busca + viagem + espera no embarque + atraso operacional + retorno + espera por próxima oferta + desvio adicional.
- Custo variável = km × custo/km + extras não reembolsados.
- Custo fixo = minutos/60 × custo fixo por hora.
- Lucro = valor recebido menos custos variável e fixo.
- R$/hora e R$/km usam lucro e ciclo completos.
- Custo detalhado pondera litros consumidos nos trechos urbanos/rodoviários, somando manutenção, pneus, óleo, depreciação e outros custos/km. O percentual rodoviário é informado pelo motorista; uma simples flag de rodovia não é tratada como percentual exato do percurso.
- Score balanceado usa a menor relação entre R$/hora e seu mínimo, e R$/km e seu mínimo. Estratégias por hora ou por km priorizam a relação correspondente. Âncoras de score: relação 0 → 0, 0,5 → 25, 1 → 60, 1,5 → 85 e 2 → 100. Não são probabilidades de lucro ou de conseguir passageiro.
- Destination Score compara receita com custo e oportunidade pós-desembarque: `100 × receita / (receita + custo de retorno e espera + oportunidade)`. É heurístico e explicável, não previsão de demanda regional.
- Cenários variam espera e duração de retorno em ±50%. Com rota verificada, a distância de retorno permanece fixa. Faixa de cenários não equivale a intervalo estatístico de confiança.

No dashboard, custos fixos usam o tempo online registrado, ou os ciclos informados quando não há turno. Não são somados duas vezes. Ofertas não concluídas não geram receita realizada.

## Dados, OCR e concorrência

Room mantém ofertas, corridas concluídas e sessões. Cada oferta guarda snapshot dos custos e das premissas usados, versão do modelo e origem. Conclusão ocorre em transação; índice único por oferta impede duplicar ganhos. Enriquecimento tardio de rota só atualiza ofertas ainda observadas e não altera um resultado concluído. Os dados observados são preenchidos separadamente, sem copiar automaticamente previsões para os campos reais.

DataStore guarda preferências. Pontos confirmados e cache de rotas usam escrita atômica. Cache: até 60 rotas por até uma hora; a chave inclui direção, coordenadas exatas, rumo, aproximação, balsas, provedor e endpoint. Não há troca automática silenciosa de fornecedor.

ML Kit Latin vem embarcado no APK. A captura usa o fluxo autorizado de MediaProjection, um serviço em primeiro plano, ImageReader e um trabalhador para quadros. O pipeline limita resolução, frequência e concorrência, ignora quadros repetidos por um intervalo e evita salvar ofertas duplicadas em 45 segundos. O cartão expira após 12 segundos sem leitura válida e é ocultado no próprio Copiloto ou ao pausar. O bitmap só é liberado depois do término do processamento nativo de OCR.

Texto espacial é unido por linha antes do parser. Valores ambíguos e trechos incompletos não produzem recomendação numérica. Quando a ordem dos trechos é inferida, o resultado é marcado para conferência. O serviço não usa AccessibilityService e não toca em outros aplicativos.

Aprendizado pessoal agrupa região normalizada, dia útil/fim de semana e blocos de quatro horas. Exige cinco medições completas; usa mediana e suavização com cinco observações equivalentes de premissa inicial. Nunca substitui uma distância viária conhecida pela média pessoal.
