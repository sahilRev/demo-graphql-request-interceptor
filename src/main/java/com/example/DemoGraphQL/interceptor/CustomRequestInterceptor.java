package com.example.DemoGraphQL.interceptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import graphql.kickstart.execution.GraphQLObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Slf4j
@Component
public class CustomRequestInterceptor extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		// Check if it's a POST req and has a specific URL pattern (or other identifying factor) for GraphQL
		if ("POST".equalsIgnoreCase(req.getMethod()) && req.getRequestURI().endsWith("/graphql")) {
			GraphQLObjectMapper mapper = GraphQLObjectMapper.newBuilder().build();
			// Extract and parse the req body
			var requestWrapper = new CachedBodyHttpServletRequest(req);
			ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(res);
			try {
				String requestBody = extractRequestBody2(requestWrapper);
				// Retrieve mutation/query body
				String mutationContent = extractMutationContent(requestBody);
				log.info("Mutation content : {}", mutationContent);

				// Count batch calls
				if (StringUtils.hasLength(mutationContent)) {
					int batchSize = countTopLevelPairs(mutationContent);
					log.info("Batch Size : {}", batchSize);
				}
				// Pass on the request to further filters
				chain.doFilter(requestWrapper, res);
			} finally {
				responseWrapper.copyBodyToResponse();
			}

		} else {
			// Continue the filter chain for non-GraphQL requests
			chain.doFilter(req, res);
		}
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	private String extractRequestBody2(CachedBodyHttpServletRequest request) throws IOException {
		try (InputStream inputStream = request.getInputStream()) {
			byte[] bytes = inputStream.readAllBytes();
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	public static String extractMutationContent(String input) {
		// Find the index of "mutation" or "query" and the opening curly brace
		int mutationIndex = input.indexOf("mutation");
		int queryIndex = input.indexOf("query");
		int startIndex = Math.max(mutationIndex, queryIndex);

		if (startIndex == -1) {
			// Neither "mutation" nor "query" found
			return null;
		}

		int openingBraceIndex = input.indexOf("{", startIndex);

		if (openingBraceIndex == -1) {
			// Opening curly brace not found
			return null;
		}

		// Find the closing curly brace
		int closingBraceIndex = findClosingBraceIndex(input, openingBraceIndex);

		if (closingBraceIndex == -1) {
			// Closing curly brace not found
			return null;
		}

		// Extract the content between the opening and closing curly braces
		String extractedContent = input.substring(openingBraceIndex + 1, closingBraceIndex).trim();

		return extractedContent;
	}

	private static int findClosingBraceIndex(String input, int openingBraceIndex) {
		int count = 1;
		int currentIndex = openingBraceIndex + 1;

		while (count > 0 && currentIndex < input.length()) {
			char currentChar = input.charAt(currentIndex);

			if (currentChar == '{') {
				count++;
			} else if (currentChar == '}') {
				count--;
			}

			currentIndex++;
		}

		return count == 0 ? currentIndex - 1 : -1;
	}

	private static int countTopLevelPairs(String content) {
		String[] lines = content.split("}\\\\n");

		int count = 0;

		for (String line : lines) {
			// Remove leading and trailing spaces
			line = line.trim();

			// Check if the line contains a key-value pair on the top level
			if (!line.isEmpty() && line.contains(":")) {
				count++;
			}
		}

		return count;
	}

}
