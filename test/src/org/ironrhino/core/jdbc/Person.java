package org.ironrhino.core.jdbc;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import org.ironrhino.common.model.Gender;

import lombok.Data;

@Data
public class Person implements Serializable {

	private static final long serialVersionUID = 7400168548407982903L;

	private String name;

	@Enumerated(EnumType.STRING)
	private Gender gender;

	@Column(name = "F_DOB")
	private LocalDate dob;

	private YearMonth since;

	private int age;

	private BigDecimal amount;

	private Map<String, String> attributes;

	private Set<String> roles;

	private PersonShadow shadow;

}
