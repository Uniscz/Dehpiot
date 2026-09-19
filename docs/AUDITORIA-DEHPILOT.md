# Auditoria de produto e engenharia — Copiloto 0.1.1 → Dehpilot

**Escopo verificável:** código integral do ZIP fornecido, documentos técnicos nele contidos, relatório anexado, print 99 e especificação Dehpilot desta solicitação. A especificação original de aproximadamente 40 páginas não está no ZIP e não foi localizada nas consultas disponíveis. Não é possível certificar comparação item a item com aquele documento. A matriz abaixo cobre os requisitos atuais e a engenharia observada na base.

**Critério de estado:** “fonte implementada” significa lógica, ligação de UI/persistência escrita, ainda sem build ou execução. Não equivale a funcionalidade validada ou V1 pronta. A execução foi bloqueada por ambiente sem SDK/Gradle e sem rede para baixar dependências.

## Achados de maior impacto

1. **Navegação:** `AppRoot` escolhia telas por `vm.tab`, sem pilha de retorno. O gesto podia encerrar a Activity em telas secundárias. Substituído por pilha testável, persistida em SavedStateHandle e consumida por PredictiveBackHandler. Cancelamento do gesto só restaura progresso visual; pop ocorre após conclusão. Raiz é Hoje. Formulários usam SaveableStateHolder/rememberSaveable e rascunhos de corrida.
2. **Custo fixo histórico:** Today multiplicava minutos do dia pelo custo/h atual, alterando retroativamente a leitura do lucro quando o custo mudava. Novos turnos guardam taxa no início. Migração aditiva 1→2 deixa o campo antigo nulo; sem histórico suficiente, usa custos já gravados dos ciclos. Não inventa taxa passada.
3. **Print real e parser:** o parser removia toda linha contendo `/km`, podendo perder o valor total se OCR juntasse preço e tarifa/km. Agora filtra cada valor monetário pelo sufixo. Endereços inline e complementos passam a ser considerados, sem resolver coordenadas silenciosamente.
4. **Base econômica subutilizada:** cálculo de retorno, custo de oportunidade, amostras, rotas e observação real existiam, mas apareciam em formulários extensos. A nova Home, resumo de direção e detalhes progressivos trazem primeiro líquido/h, retorno e risco.
5. **Persistência e confiança:** snapshots por oferta já estavam corretamente implementados e foram mantidos. O novo combustível não reprecifica a corrida anterior. Custo/km manual legado continua manual até alteração explícita.
6. **Aprendizado manual:** `learn()` preenchia números, mas `calculate()` reconstruía premissas sem a contagem de amostras. A contagem passa a acompanhar o cálculo e é invalidada ao editar destino/espera.
7. **Histórico longo:** a tela compunha todos os cards ao mesmo tempo e CSV dependia da lista limitada em memória. Lista virtualizada com detalhes em diálogo; CSV agora consulta todos os registros armazenados em transação. UI continua limitada a 10 mil registros recentes, explicitamente indicada.
8. **Ações repetidas:** salvar oferta e iniciar/encerrar turno podiam ser disparados várias vezes. Salvamento possui guarda; início/fim de turno idempotentes evitam que um segundo toque encerre o turno recém-iniciado. Conclusão transacional original preservada.
9. **Dados frágeis:** score de oferta não mede demanda global. Insights são apresentados como observações pessoais, com janelas/amostras e ausência explícita de conclusão sem dados.
10. **Fluxo de custo simples:** exigia que a pessoa já soubesse R$/km. Agora novos usuários informam preço e consumo, e usuários existentes podem manter o custo manual. Custos detalhados continuam acessíveis.

## Matriz de requisitos atuais

| Nº | Requisito | Base 0.1.1 | Alteração em fonte e limite atual |
|---:|---|---|---|
| 1 | Produto mobile com hierarquia e movimento | Sequência de cards/formulários | Shell, hierarquia financeira, transição curta, lista e disclosure. Acabamento visual não validado em tela real |
| 2 | Identidade Dehpilot | Copiloto | Nome, textos, ícone, cores, rootProject e user-agent alterados. Namespace/banco mantidos para compatibilidade |
| 3 | Home contextual | Dashboard básico | Hero líquido, hora online, meta/ETA, tempo, km vazio, produtividade, evolução e qualidade observada |
| 4 | Modo direção | Apenas overlay básico | Tela própria, análise recente, risco e expansão. Overlay preservado e atualizado. Sem certificação de legibilidade em trânsito |
| 5 | Análises | Totais e lista por região | Dia/7/30 dias, períodos anteriores, barras diárias, produtividade, destinos/horários e custos. Não inclui todos os modelos temporais avançados |
| 6 | OCR painel | Ausente | Câmera/galeria, EXIF/downsample, OCR local, parser próprio, candidatos, confirmação e fallback manual |
| 7 | Etanol/custo real | Combustível no modo detalhado | Preço/consumo simples, precisão interna, histórico de alterações, integração com motor e snapshots |
| 8 | Custo simples/detalhado | Existentes mas difíceis | Simples por combustível para novos usos; detalhado preservado; legado manual não é apagado |
| 9 | Voltar Android | Sem back stack | Pilha persistida, predictive back, botão interno, wizard retorna etapas. Falta executar gesto real/cancelado |
| 10 | Navegação principal | Cinco itens misturando ferramentas | Hoje, Mapa, Histórico, Análises, Mais; diagnóstico/configuração secundários |
| 11 | Mapa pessoal | Mapa escondido dentro de roteamento | Mapa na aba principal, pontos por rentabilidade observada e amostra. Não é heatmap de demanda |
| 12 | Hora ruim | Comparação parcial de janelas | Insight visível com 5 amostras por janela, variação percentual e ressalva sobre cobertura da captura |
| 13 | Insights reais | Aprendizado regional básico | Histórico pessoal com mínimos, km vazio vs período anterior, horários por múltiplos dias, custo/km. Não inventa evolução com poucas amostras |
| 14 | Meta | Bruta/líquida já existiam | Edição em bottom sheet, restante, progresso, ETA e ritmo necessário até prazo |
| 15 | Voltar para casa | Usuário trocava região manualmente | Casa persistida, troca do alvo viário, restauração de região de trabalho. Sem bônus arbitrário nem comparação simultânea de duas rotas |
| 16 | Histórico | Cards extensos e status técnicos | Linhas, filtros, detalhes, previsto/real, erro relativo e rascunho. Sem importação automática de viagens |
| 17 | Calibração | Erro do líquido resumido | MAE R$/h, erro relativo, tamanho da amostra, linguagem explícita. Não treina um novo modelo de score |
| 18 | Confiança | Indicadores presentes | Preservados, acrescidos de amostras e distinção de observação/estimativa. Não há probabilidade estatisticamente calibrada |
| 19 | OCR debugger | Na tela normal de análise | Transferido para Mais; imagem/texto/caixas sobre a imagem, latência OCR e da importação até tentativa de rota. Latência completa da captura ao overlay pendente |
| 20 | Simulador | Formulário + resultado extenso | Campos preservados, resultado trazido à vista, score e retorno prioritários, explicação expansível |
| 21 | Onboarding | Direcionava a ajustes | Cinco etapas, foto opcional, custo, meta/estratégia, teste OCR e primeiro uso; sem exigir todas as permissões para começar |
| 22 | Permissões | Solicitações do sistema em cadeia | Página com explicações por finalidade, localização contextual no mapa. Fluxo por fabricante ainda não testado |
| 23 | Microinterações | Poucas | Progresso/expansão/transição, háptico opcional na navegação e feedback existente de leitura. Não há animação infinita decorativa |
| 24 | Estados | Leitura e turno básicos | Sem turno, em turno, aguardando, corrida, reposicionamento, casa, OCR ocupado/erro, offline e poucos dados. Estados de condução ainda manuais |
| 25 | Empty states | Texto sem próximo passo | CTA para simular/registrar e explicação do aprendizado nas áreas sem dados |
| 26 | Feedback | Snackbars parciais | Confirmações de custo, meta, decisões, captura, turno e rotas. Ainda requer teste ponta a ponta |
| 27 | Performance | Core leve; listas eager | LazyColumn, downsample, limite de histórico de custos, overlay não reconstruído sem mudança, atualização temporal só em STARTED. Sem benchmarks medidos |
| 28 | Preservação | Base funcional existente | Módulos centrais mantidos; adições compatíveis e migração não destrutiva. Suítes antigas preservadas; regressão não executada |
| 29 | Auditoria original | Documento não incluído | Código/relatório auditados; comparação integral com original bloqueada por fonte ausente |
| 30 | Product thinking | Recursos organizados por implementação | Decisões de jornada e discovery abaixo. Não alegar validação com motorista real |
| 31 | QA visual | Sem evidência nova | Revisão estrutural/estática realizada; screenshot/emulador não disponíveis, refinamento visual pendente |
| 32 | Testes | 29 JVM + 10 Android em fonte | 65 JVM + 27 Android escritos. **0 executados nesta revisão** |
| 33 | Build final | Wrapper fornecido | Tentativas bloquearam antes das tarefas. **Sem APK novo** |
| 34 | Artefatos/checkpoint | ZIP recebido | Checkpoint preservado; ZIP completo, código, README, auditoria e logs. APK não pode ser entregue |
| 35 | Produto pronto | V0.1.1 | **Critério não atingido:** falta compilar, executar, instalar e validar as jornadas |

## Product discovery: teste mental de 30 dias

| Oportunidade / fricção | Decisão e valor | Estado |
|---|---|---|
| Economizar combustível | Atualizar preço e média por foto; reconhecer mudança no custo/km | Fonte implementada |
| Aumentar R$/h | Medir tempo online, inclusive espera, e mostrar mínimos/ritmo necessário | Fonte implementada; depende de turno corretamente registrado |
| Reduzir km vazio | Evidenciar retorno viário e parcela vazia; casa como alvo real | Fonte implementada |
| Antecipar deterioração | Comparar janelas reais de ofertas, sem apresentar mercado global | Fonte implementada |
| Evitar decisões ruins | Expirar análises antigas em direção e distinguir retorno estimado | Fonte implementada |
| Antecipar problemas | Mostrar ausência de conexão, amostra pequena, custo alterado | Fonte implementada |
| Automação legítima | OCR local e atualização contextual, sem tocar em Uber/99 | Engenharia original preservada |
| Aproveitar histórico | Filtros, detalhe previsto/real e exportação integral | Fonte implementada |
| Padrões após semanas | Horários com 5 ciclos em 3 dias; regiões com mínimo de 5 | Fonte implementada; não significa causalidade |
| Exceções | Legado sem custo fixo, meta com saldo negativo, foto ambígua, rotação, duplo toque | Tratamento em fonte + testes escritos |
| Irritação por perda de formulário | Rascunhos de oferta/resultado e estado de navegação | Fonte implementada; teste de morte de processo pendente |
| Perda de dados ao trocar celular | Backup restaurável completo | Pendente; CSV não resolve toda a restauração |
| Turno esquecido aberto por dias | Correção auditável de horário de encerramento | Pendente; não inferir automaticamente que motorista encerrou |
| Jornada atravessando meia-noite | Relatório por turno além do calendário | Parcial: turnos persistidos, custos clipados; repartição exata e relatório por turno pendentes |

## Solicitações criticadas e decisões

| Solicitação original | Problema identificado | Solução escrita / motivo |
|---|---|---|
| Qualidade do mercado agora | Poucas ofertas observadas não representam demanda total | “Qualidade das ofertas observadas”, janela e amostra visíveis |
| Score maior para casa | Bônus arbitrário poderia aprovar ciclo deficitário | Mudar alvo da rota para casa; tempo/km reais alteram naturalmente o score |
| Precisão em percentual | MAPE explode com lucro próximo de zero; porcentagem de “acerto” inventada | MAE R$/h e erro relativo com exclusão explicitada de resultados entre −1 e +1 R$/h |
| Detectar automaticamente todos os estados | Não há prova de passageiro/viagem sem integração confiável | Estado manual e sinais reais de captura/GPS/rede; não simular detecção |
| Fotografar painel e aplicar | OCR pode confundir autonomia/instantâneo com consumo | Parser só de consumo com unidade, confirmação e correção |
| Mudar nome do aplicativo | Alterar package criaria outra instalação e banco isolado | Trocar marca exibida; manter applicationId e banco |
| App “vivo” | Loop visual/GPS permanente gastaria bateria sem benefício | Animação por mudança, clock de 15 s apenas com UI ativa, sem tracking novo permanente |

## Gate de continuidade

Não declarar esta revisão concluída. Primeiro liberar ambiente de compilação, executar suites, gerar schema Room 2 e conferir migração, renderizar telas, corrigir defeitos, testar captura/OCR real, reutilizar assinatura anterior quando possível, preservar APK e só então avaliar o critério de produto V1.
