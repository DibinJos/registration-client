package io.mosip.registration.service.bio.impl;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;

import org.springframework.stereotype.Service;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.dto.packetmanager.BiometricsDto;
import io.mosip.registration.service.bio.BiometricQualityCheckService;

/**
 * Implementation of BiometricQualityCheckService
 * Calls external quality check API for biometric validation
 *
 * @author Claude
 * @since 1.0.0
 */
@Service
public class BiometricQualityCheckServiceImpl implements BiometricQualityCheckService {

	private static final Logger LOGGER = AppConfig.getLogger(BiometricQualityCheckServiceImpl.class);

	// API Configuration - Update this to your deployed API URL
	private static final String QUALITY_CHECK_API_URL = "https://mosip-quality-api.onrender.com/quality/check";
	private static final String API_HEALTH_URL = "https://mosip-quality-api.onrender.com/health";
	private static final int API_TIMEOUT_SECONDS = 30;

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(java.time.Duration.ofSeconds(API_TIMEOUT_SECONDS))
			.build();

	@Override
	public boolean checkBiometricQuality(List<BiometricsDto> biometricsList) {
		LOGGER.info("BiometricQualityCheckServiceImpl: Starting quality check for {} biometrics",
				biometricsList != null ? biometricsList.size() : 0);

		if (biometricsList == null || biometricsList.isEmpty()) {
			LOGGER.warn("BiometricQualityCheckServiceImpl: Empty biometrics list");
			return false;
		}

		// Check API availability first
		if (!isAPIAvailable()) {
			LOGGER.error("BiometricQualityCheckServiceImpl: Quality check API is not available");
			return true; // Allow capture if API is down (fail-open approach)
		}

		boolean allPassed = true;
		for (BiometricsDto biometric : biometricsList) {
			QualityCheckResult result = checkSingleBiometric(biometric);
			if (result != null) {
				LOGGER.info("BiometricQualityCheckServiceImpl: Quality check result - {}", result);
				if (!result.passed) {
					allPassed = false;
					LOGGER.warn("BiometricQualityCheckServiceImpl: Quality check failed for {}: {}",
							result.bioAttribute, result.recommendations);
				}
			} else {
				LOGGER.error("BiometricQualityCheckServiceImpl: Failed to get quality check result");
				allPassed = false;
			}
		}

		return allPassed;
	}

	@Override
	public QualityCheckResult checkSingleBiometric(BiometricsDto biometric) {
		try {
			if (biometric == null || biometric.getBioData() == null) {
				LOGGER.warn("BiometricQualityCheckServiceImpl: Invalid biometric data");
				return null;
			}

			// Encode biometric data to Base64
			String imageBase64 = Base64.getEncoder().encodeToString(biometric.getBioData());

			// Build request JSON
			String requestJson = buildRequestJson(imageBase64, biometric);
			LOGGER.debug("BiometricQualityCheckServiceImpl: Sending quality check request for {}",
					biometric.getBioAttribute());

			// Create HTTP request
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(QUALITY_CHECK_API_URL))
					.header("Content-Type", "application/json")
					.timeout(java.time.Duration.ofSeconds(API_TIMEOUT_SECONDS))
					.POST(HttpRequest.BodyPublishers.ofString(requestJson))
					.build();

			// Send request and get response
			HttpResponse<String> response = HTTP_CLIENT.send(request,
					HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				return parseQualityCheckResponse(response.body());
			} else {
				LOGGER.error("BiometricQualityCheckServiceImpl: API returned status code {}: {}",
						response.statusCode(), response.body());
				return null;
			}

		} catch (Exception e) {
			LOGGER.error("BiometricQualityCheckServiceImpl: Error during quality check: {}", e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Build JSON request body for quality check API
	 */
	private String buildRequestJson(String imageBase64, BiometricsDto biometric) {
		return String.format(
				"{\"image\":\"%s\",\"biometric_type\":\"%s\",\"bio_attribute\":\"%s\",\"field_id\":\"biometrics\"}",
				imageBase64,
				getBiometricType(biometric),
				biometric.getBioAttribute() != null ? biometric.getBioAttribute() : "Unknown");
	}

	/**
	 * Get biometric type string from BiometricsDto
	 */
	private String getBiometricType(BiometricsDto biometric) {
		String bioType = biometric.getBioType();
		if (bioType != null) {
			// Normalize to API format: FINGERPRINT, IRIS, FACE
			if (bioType.toLowerCase().contains("finger")) {
				return "FINGERPRINT";
			} else if (bioType.toLowerCase().contains("iris")) {
				return "IRIS";
			} else if (bioType.toLowerCase().contains("face")) {
				return "FACE";
			}
		}
		return "FINGERPRINT"; // Default
	}

	/**
	 * Parse quality check API response
	 */
	private QualityCheckResult parseQualityCheckResponse(String responseBody) {
		try {
			JsonObject jsonResponse = Json.createReader(new StringReader(responseBody)).readObject();

			boolean passed = jsonResponse.getBoolean("passed");
			double qualityScore = jsonResponse.getJsonNumber("quality_score").doubleValue();
			String biometricType = jsonResponse.getString("biometric_type");
			String bioAttribute = jsonResponse.getString("bio_attribute");
			String recommendations = jsonResponse.getString("recommendations");
			String details = jsonResponse.getString("details");

			return new QualityCheckResult(passed, qualityScore, biometricType, bioAttribute,
					recommendations, details);

		} catch (Exception e) {
			LOGGER.error("BiometricQualityCheckServiceImpl: Error parsing quality check response: {}",
					e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Check if quality check API is available
	 */
	private boolean isAPIAvailable() {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(API_HEALTH_URL))
					.timeout(java.time.Duration.ofSeconds(5))
					.GET()
					.build();

			HttpResponse<String> response = HTTP_CLIENT.send(request,
					HttpResponse.BodyHandlers.ofString());

			boolean available = response.statusCode() == 200;
			LOGGER.info("BiometricQualityCheckServiceImpl: API health check - Available: {}", available);
			return available;

		} catch (Exception e) {
			LOGGER.warn("BiometricQualityCheckServiceImpl: API health check failed: {}", e.getMessage());
			return false;
		}
	}
}
