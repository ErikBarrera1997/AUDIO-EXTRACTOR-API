package com.msservices.app.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

public class DeviceConnectionInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(DeviceConnectionInterceptor.class);
	private static final Set<String> knownDevices = ConcurrentHashMap.newKeySet();

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String deviceId = resolveDeviceId(request);
		if (deviceId != null && knownDevices.add(deviceId)) {
			log.info("Device connected");
		}
		return true;
	}

	private String resolveDeviceId(HttpServletRequest request) {
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isBlank()) {
			return xForwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

}