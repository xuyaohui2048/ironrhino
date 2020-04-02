package org.ironrhino.batch.tasklet.http;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.UnexpectedJobExecutionException;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Slf4j
public class WebhookTask implements Tasklet {

	private URI url;

	private String body = "";

	private Map<String, String> headers = Collections.singletonMap(HttpHeaders.CONTENT_TYPE,
			MediaType.APPLICATION_JSON_VALUE);

	private HttpMethod method = HttpMethod.POST;

	private Pattern responseFailurePattern;

	private boolean suppressFailure;

	@Override
	public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
		RestTemplate rt = new RestTemplate();
		HttpHeaders httpHeaders = new HttpHeaders();
		for (Map.Entry<String, String> entry : headers.entrySet())
			httpHeaders.add(entry.getKey(), entry.getValue());
		RequestEntity<String> request = new RequestEntity<>(body, httpHeaders, method, url);
		validate(rt.exchange(request, String.class));
		return RepeatStatus.FINISHED;
	}

	protected void validate(ResponseEntity<String> response) throws Exception {
		String responseBody = response.getBody();
		if (response.getStatusCode().is2xxSuccessful() && !isFailure(responseBody)) {
			log.info("Requested {} with [{}] and received [{}]", url, body, responseBody);
		} else {
			if (suppressFailure) {
				log.error("Requested {} with [{}] and received [{}] with status code {}", url, body, responseBody,
						response.getStatusCodeValue());
			} else {
				throw new UnexpectedJobExecutionException(
						String.format("Requested %s with [%s] and received [%s] with status code %d", url, body,
								responseBody, response.getStatusCodeValue()));
			}
		}
	}

	protected boolean isFailure(String responseBody) {
		if (responseFailurePattern != null)
			return responseFailurePattern.matcher(responseBody).find();
		return false;
	}

}
