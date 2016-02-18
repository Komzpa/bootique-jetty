package com.nhl.bootique.jetty;

import java.util.EventListener;
import java.util.Set;

import org.eclipse.jetty.server.Server;

import com.google.inject.Binder;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.nhl.bootique.BQCoreModule;
import com.nhl.bootique.ConfigModule;
import com.nhl.bootique.config.ConfigurationFactory;
import com.nhl.bootique.env.DefaultEnvironment;
import com.nhl.bootique.jetty.command.ServerCommand;
import com.nhl.bootique.jetty.server.ServerFactory;
import com.nhl.bootique.jetty.servlet.DefaultServletContainerState;
import com.nhl.bootique.jetty.servlet.ServletContainerState;
import com.nhl.bootique.log.BootLogger;
import com.nhl.bootique.shutdown.ShutdownManager;

public class JettyModule extends ConfigModule {

	/**
	 * @param binder
	 *            DI binder passed to the Module that invokes this method.
	 * @since 0.11
	 * @return returns a {@link Multibinder} for container servlets.
	 */
	public static Multibinder<MappedServlet> contributeServlets(Binder binder) {
		return Multibinder.newSetBinder(binder, MappedServlet.class);
	}

	/**
	 * @param binder
	 *            DI binder passed to the Module that invokes this method.
	 * @since 0.11
	 * @return returns a {@link Multibinder} for container servlets.
	 */
	public static Multibinder<MappedFilter> contributeFilters(Binder binder) {
		return Multibinder.newSetBinder(binder, MappedFilter.class);
	}

	/**
	 * @param binder
	 *            DI binder passed to the Module that invokes this method.
	 * @since 0.12
	 * @return returns a {@link Multibinder} for container listeners.
	 */
	public static Multibinder<EventListener> contributeListeners(Binder binder) {
		return Multibinder.newSetBinder(binder, EventListener.class);
	}

	private String context;
	private int port;

	public JettyModule(String configPrefix) {
		super(configPrefix);
	}

	public JettyModule() {
	}

	public JettyModule context(String context) {
		this.context = context;
		return this;
	}

	public JettyModule port(int port) {
		this.port = port;
		return this;
	}

	@Override
	public void configure(Binder binder) {

		BQCoreModule.contributeCommands(binder).addBinding().to(ServerCommand.class).in(Singleton.class);

		if (context != null) {
			BQCoreModule.contributeProperties(binder)
					.addBinding(DefaultEnvironment.FRAMEWORK_PROPERTIES_PREFIX + "." + configPrefix + ".context")
					.toInstance(context);
		}

		if (port > 0) {
			BQCoreModule.contributeProperties(binder)
					.addBinding(DefaultEnvironment.FRAMEWORK_PROPERTIES_PREFIX + "." + configPrefix + ".connector.port")
					.toInstance(String.valueOf(port));
		}

		// trigger extension points creation and provide default contributions

		JettyModule.contributeServlets(binder);
		JettyModule.contributeFilters(binder);
		JettyModule.contributeListeners(binder);

		// register default listeners
		JettyModule.contributeListeners(binder).addBinding().to(DefaultServletContainerState.class);
	}

	@Singleton
	@Provides
	ServletContainerState createStateTracker(DefaultServletContainerState stateImpl) {
		return stateImpl;
	}

	@Singleton
	@Provides
	DefaultServletContainerState createStateTrackerImpl() {
		return new DefaultServletContainerState();
	}

	@Singleton
	@Provides
	Server createServer(ServerFactory factory, Set<MappedServlet> servlets, Set<MappedFilter> filters,
			Set<EventListener> listeners, BootLogger bootLogger, ShutdownManager shutdownManager) {

		Server server = factory.createServer(servlets, filters, listeners);

		shutdownManager.addShutdownHook(() -> {
			bootLogger.trace(() -> "stopping Jetty...");
			server.stop();
		});

		return server;
	}

	@Singleton
	@Provides
	ServerFactory createServerFactory(ConfigurationFactory configFactory) {
		return configFactory.config(ServerFactory.class, configPrefix);
	}
}
