package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

public final class StaticLoggerBinder implements LoggerFactoryBinder {
	public static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

	private static final String LOGGER_FACTORY_CLASS_STR = NOPLoggerFactory.class.getName();
	private final ILoggerFactory loggerFactory = new NOPLoggerFactory();

	private StaticLoggerBinder() {
	}

	public static StaticLoggerBinder getSingleton() {
		return SINGLETON;
	}

	@Override
	public ILoggerFactory getLoggerFactory() {
		return loggerFactory;
	}

	@Override
	public String getLoggerFactoryClassStr() {
		return LOGGER_FACTORY_CLASS_STR;
	}
}
