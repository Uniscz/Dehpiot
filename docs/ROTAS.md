# Roteamento free-first: decisão técnica

Decisão preservada da implementação anterior: OSM como fonte de rede viária, MapLibre para renderização e OSRM/Valhalla para cálculo real. OSM é dado cartográfico; MapLibre desenha mapas. Nenhum dos dois, sozinho, calcula uma rota de carro.

| Opção | Uso nesta versão | Motivo |
|---|---|---|
| OSRM / FOSSGIS | Provedor padrão implementado | Rota automobilística rápida, geometria GeoJSON, manobras, rumo e aproximação |
| Valhalla / FOSSGIS | Segundo provedor implementado | Custeio auto e exclusão efetiva de balsas; geometria GeoJSON ou polyline6 normalizada |
| GraphHopper | Avaliado, sem dependência nesta entrega | Hospedagem própria viável; plano hospedado gratuito consultado restringe uso não comercial e tem limite diário |
| Google / HERE / TomTom | Contrato preparado para implementação posterior | Serviços pagos exigem contratação/chaves e normalização própria; não são necessários para o APK atual funcionar |
| MapLibre + OSM raster | Mapa implementado | Inspeção e confirmação dos pontos e da geometria de rota, sem chave proprietária |

Provedores públicos padrão: `https://routing.openstreetmap.de/routed-car` e `https://valhalla1.openstreetmap.de`. Nenhum servidor foi publicado em nome do usuário. Endpoints podem ser alterados na interface para infraestrutura própria compatível.

As requisições são HTTPS, identificadas por User-Agent do Copiloto e espaçadas em pelo menos 1,5 segundo. Há limites de resposta, timeouts e cache local. O mapa é carregado ao ser aberto pelo usuário, mantém atribuição, usa cache HTTP e desativa prefetch. Não há download em massa de mapas.

Os serviços públicos são apropriados para teste e uso pessoal moderado dentro das políticas publicadas, sem SLA. Não trate esses endpoints como capacidade gratuita ilimitada para distribuição comercial. Antes de escalar, use hospedagem própria de um motor livre ou um fornecedor contratado. O domínio econômico permanece o mesmo.

## Particularidades verificadas

O endpoint OSRM público selecionado rejeita `exclude=ferry`; a integração não envia esse parâmetro. Ao ativar Evitar balsas, a interface usa Valhalla com `costing_options.auto.exclude_ferries=true`.

Mesmo com `shape_format=geojson`, a resposta Valhalla observada trouxe shape codificado. O decoder suporta tanto GeoJSON quanto polyline com precisão de seis casas. Distâncias e durações são normalizadas para km e minutos.

As restrições de vias dependem do grafo e da qualidade/atualização dos dados OSM. Mudanças recentes, interdições ou regras ausentes podem não estar refletidas. A indicação de pedágio não equivale a preço confirmado. O tempo fornecido não inclui trânsito ao vivo; espera de balsa e tarifas precisam de conferência.

Endereços são buscados apenas por ação explícita, usando o Geocoder disponível no Android e escolha do resultado pelo usuário. Não há geocodificação automática de endereço OCR incerto nem dependência do Nominatim público. Coordenadas confirmadas podem ser reutilizadas por correspondência exata de nome.

## Fontes primárias consultadas

- [Políticas e descrição do serviço de rotas FOSSGIS](https://routing.openstreetmap.de/about.html).
- [API OSRM, Route service, bearings, approaches e radiuses](https://project-osrm.org/docs/v5.24.0/api/).
- [API Valhalla, automobile costing e exclusões](https://valhalla.github.io/valhalla/api/route/api-reference/).
- [MapLibre Native Android](https://maplibre.org/maplibre-native/android/api/).
- [GraphHopper: planos e condições](https://www.graphhopper.com/pricing/).
- [Política de tiles OSM](https://operations.osmfoundation.org/policies/tiles/).
- [Política do Nominatim público](https://operations.osmfoundation.org/policies/nominatim/).

Preços, termos e disponibilidade de serviços externos podem mudar; confirme-os antes de ampliar o uso.
