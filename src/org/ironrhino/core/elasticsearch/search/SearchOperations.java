package org.ironrhino.core.elasticsearch.search;

import java.util.List;

import org.ironrhino.core.elasticsearch.Constants;
import org.ironrhino.core.elasticsearch.ElasticsearchEnabled;
import org.ironrhino.rest.client.JsonPointer;
import org.ironrhino.rest.client.RestApi;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@ElasticsearchEnabled
@RestApi(apiBaseUrl = Constants.VALUE_ELASTICSEARCH_URL, treatNotFoundAsNull = true, dateFormat = Constants.DATE_FORMAT)
public interface SearchOperations<T> {

	@GetMapping("/{index}/_count")
	@JsonPointer("/count")
	long count(@PathVariable String index, @RequestParam("q") String query);

	@GetMapping("/{index}/_search")
	SearchResult<T> search(@PathVariable String index, @RequestParam("q") String query);

	@GetMapping("/{index}/_search")
	SearchResult<T> search(@PathVariable String index, @RequestParam("q") String query, @RequestParam int from,
			@RequestParam int size);

	@GetMapping("/{index}/_search")
	SearchResult<T> search(@PathVariable String index, @RequestParam("q") String query, @RequestParam String scroll);

	@GetMapping("/{index}/_search")
	SearchResult<T> search(@PathVariable String index, @RequestParam("q") String query, @RequestParam String scroll,
			@RequestParam int size);

	@GetMapping("/_search/scroll")
	SearchResult<T> scroll(@RequestParam String scroll, @RequestParam("scroll_id") String scrollId);

	@DeleteMapping("/_search/scroll/{scrollId}")
	void clearScroll(@PathVariable String scrollId);

	@PostMapping("/{index}/_search?size=0")
	@JsonPointer("/aggregations/aggs/buckets")
	List<AggregationBucket> aggregate(@PathVariable String index, @RequestBody TermsAggregation aggregation);

	@PostMapping("/{index}/_search?size=0")
	@JsonPointer("/aggregations/aggs/buckets")
	List<AggregationBucket> aggregate(@PathVariable String index, @RequestBody HistogramAggregation aggregation);

	@PostMapping("/{index}/_search?size=0")
	@JsonPointer("/aggregations/aggs/buckets")
	List<AggregationBucket> aggregate(@PathVariable String index, @RequestBody DateHistogramAggregation aggregation);

}
