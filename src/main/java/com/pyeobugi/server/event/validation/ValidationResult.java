package com.pyeobugi.server.event.validation;

import com.pyeobugi.server.event.domain.RejectCode;
import com.pyeobugi.server.event.domain.ValidatedEvent;

public sealed interface ValidationResult {

	record Valid(ValidatedEvent event) implements ValidationResult {
	}

	record Rejected(RejectCode code) implements ValidationResult {
	}

}
