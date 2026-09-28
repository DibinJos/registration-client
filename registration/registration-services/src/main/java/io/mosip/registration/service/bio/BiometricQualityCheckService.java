package io.mosip.registration.service.bio;

import java.util.List;
import io.mosip.registration.dto.packetmanager.BiometricsDto;

/**
 * Service for checking biometric quality using external API
 *
 * @author Claude
 * @since 1.0.0
 */
public interface BiometricQualityCheckService {

	/**
	 * Check quality of captured biometrics using external API
	 *
	 * @param biometricsList List of captured biometrics
	 * @return true if all biometrics passed quality check, false otherwise
	 */
	public boolean checkBiometricQuality(List<BiometricsDto> biometricsList);

	/**
	 * Get quality check result for a single biometric
	 *
	 * @param biometric Single biometric DTO
	 * @return QualityCheckResult object with details
	 */
	public QualityCheckResult checkSingleBiometric(BiometricsDto biometric);

	/**
	 * Result object for quality check
	 */
	public static class QualityCheckResult {
		public boolean passed;
		public double qualityScore;
		public String biometricType;
		public String bioAttribute;
		public String recommendations;
		public String details;

		public QualityCheckResult(boolean passed, double qualityScore, String biometricType,
				String bioAttribute, String recommendations, String details) {
			this.passed = passed;
			this.qualityScore = qualityScore;
			this.biometricType = biometricType;
			this.bioAttribute = bioAttribute;
			this.recommendations = recommendations;
			this.details = details;
		}

		@Override
		public String toString() {
			return "QualityCheckResult{" + "passed=" + passed + ", qualityScore=" + qualityScore
					+ ", biometricType='" + biometricType + '\'' + ", bioAttribute='" + bioAttribute
					+ '\'' + ", recommendations='" + recommendations + '\'' + ", details='" + details
					+ '\'' + '}';
		}
	}
}
