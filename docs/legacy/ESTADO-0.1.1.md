# Estado funcional desta entrega

Esta versão restaura e consolida a arquitetura definida anteriormente. A prioridade é um APK instalável e código preservado. Não representa a implementação de todas as ideias avançadas da especificação de 40 páginas.

| Funcionalidade | Estado no APK |
|---|---|
| Cálculo do ciclo completo, custos simples/detalhados e estratégias por hora/km/balanceada | Implementado; motor com testes automatizados |
| Roteamento viário real e modular | Implementado com dois provedores: OSRM e Valhalla |
| Retorno de BR, detour, sentidos, acessos, manobras e geometria | Calculados pelo grafo automobilístico; efeito econômico integrado e aviso de acesso difícil |
| Distância em linha reta | Apenas indicador comparativo; nunca substitui rota de carro |
| Destination Score e oportunidade | Implementados com fórmula explícita; score regional fica pendente sem rota confirmada |
| Mapa MapLibre, escolha de ponto, GPS e favoritos | Implementados; busca de endereço depende de Geocoder disponível no dispositivo |
| Rota automática após OCR | Para nomes que correspondam a destinos previamente confirmados e região de retorno salva |
| OCR de print importado | Implementado com ML Kit Latin embarcado |
| Captura contínua autorizada e overlay | Implementados com MediaProjection, serviço visível, pausa/parada e expiração do cartão |
| Voz e vibração | Implementadas e opcionais; voz depende de mecanismo TTS/idioma instalado |
| Demonstração e parser de texto | Implementados, sem receita ou histórico fictício |
| Histórico, decisões e previsto × real | Implementados com conclusão transacional e proteção contra duplicação |
| Dashboard, metas e turnos | Implementados a partir de dados registrados pelo usuário |
| Aprendizado pessoal por região/período | Implementado com mínimo de cinco medições completas e suavização |
| Indicador de deterioração de ofertas | Comparação de score médio entre duas janelas de 25 min, com cinco ofertas por janela |
| Voltar para casa | Casa pode ser a região de retorno; deslocamento final entra no custo e score |
| Exportação CSV, retenção e exclusão | Implementados localmente |
| Trânsito ao vivo e preços confiáveis de pedágio | Não fornecidos pelos provedores atuais; capacidades separadas e extras manuais |

## Limites reais

1. A qualidade do acesso calculado depende da posição/rumo confirmados e do mapa OSM. Proximidade geográfica isolada não comprova viabilidade. Rotas não conhecem toda interdição ou restrição temporária. Quando a via está ausente, não se inventa um caminho.
2. Novos endereços de OCR não são geocodificados silenciosamente. Se não houver destino previamente confirmado, é necessário escolher o ponto em Regiões para concluir a análise regional. Isso evita enviar uma rota aparentemente exata a partir de um endereço ambíguo.
3. O OCR é heurístico e precisa de valor e dos dois pares distância/tempo. Layouts novos, cartões cortados, fontes pequenas e imagens protegidas podem falhar. Trechos sem rótulo usam a ordem da leitura com aviso explícito. A compatibilidade com as versões e telas reais de Uber/99 no celular do usuário precisa de validação em uso.
4. MediaProjection/overlay está implementado, mas esta entrega não certifica captura contínua ponta a ponta em cada fabricante, versão Android ou app de corrida. O relatório distingue execução real de testes de código e cobertura ainda não revalidada.
5. Não há importação automática de viagens concluídas nem distinção automática de todos os estados de condução. Ganhos, km e tempos reais dependem dos registros do motorista. O aprendizado é limitado pela quantidade e qualidade desses registros.
6. O painel regional é estatístico pessoal, sem mapa de calor de demanda, previsão global de passageiros ou probabilidade calibrada de obter corrida. Não há previsão de tráfego ou demanda em tempo real.
7. Não há todos os perfis avançados imaginados no PDF, treinamento de modelo ML, otimização automática entre múltiplas regiões, navegação curva a curva com voz ou modelo específico de aeroporto/eventos. Atrasos e despesas podem ser informados nos campos operacionais.
8. Mapas novos e rotas sem cache dependem de internet e do serviço público escolhido. Cache viário dura uma hora; não há motor offline de ruas embarcado nem mapa offline de todo o Brasil. Economia, OCR, preferências e histórico funcionam localmente.
9. O APK de teste é assinado com chave de desenvolvimento. Para distribuição ampla, é preciso chave definitiva e avaliar infraestrutura/políticas dos provedores. Isso não impede instalar a versão entregue para teste pessoal.

Esses limites não incluem ausência de roteamento: o roteamento real está implementado, faz consultas reais e participa dos cálculos do APK.
