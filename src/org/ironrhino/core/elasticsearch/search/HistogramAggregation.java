package org.ironrhino.core.elasticsearch.search;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.Value;

@Value
public class HistogramAggregation {

	private Map<String, Map<String, Map<String, Object>>> aggregations;

	public static HistogramAggregation of(String field, int interval) {
		Map<String, Object> map = new HashMap<>();
		map.put("field", field);
		map.put("interval", interval);
		return new HistogramAggregation(Collections.singletonMap("aggs", Collections.singletonMap("histogram", map)));
	}

}
