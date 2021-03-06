package org.ironrhino.security.component;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ironrhino.core.service.EntityManager;
import org.ironrhino.core.spring.configuration.ClassPresentConditional;
import org.ironrhino.core.spring.security.DefaultAuthenticationSuccessHandler;
import org.ironrhino.security.model.LoginRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@Primary
@ClassPresentConditional("org.ironrhino.security.model.LoginRecord")
public class LoginRecordAuthenticationSuccessHandler extends DefaultAuthenticationSuccessHandler {

	@Autowired
	private EntityManager<LoginRecord> entityManager;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws ServletException, IOException {
		super.onAuthenticationSuccess(request, response, authentication);
		Object principal = authentication.getPrincipal();
		String username;
		if (principal instanceof UserDetails) {
			username = ((UserDetails) principal).getUsername();
		} else {
			username = String.valueOf(principal);
		}
		entityManager.setEntityClass(LoginRecord.class);
		LoginRecord loginRecord = new LoginRecord();
		loginRecord.setUsername(username);
		loginRecord.setAddress(request.getRemoteAddr());
		entityManager.save(loginRecord);
	}

}
