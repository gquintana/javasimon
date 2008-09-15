package org.javasimon;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * EnabledManager implements methods called from SimonManager if the Simon API is enabled.
 *
 * @author <a href="mailto:virgo47@gmail.com">Richard "Virgo" Richter</a>
 * @created Aug 16, 2008
 */
class EnabledManager implements Manager {
	static final Manager INSTANCE = new EnabledManager();

	private static final int CLIENT_CODE_STACK_INDEX = 3;

	private final Map<String, AbstractSimon> allSimons = new HashMap<String, AbstractSimon>();

	private UnknownSimon rootSimon;

	@Override
	public Simon getSimon(String name) {
		return allSimons.get(name);
	}

	@Override
	public void destroySimon(String name) {
		AbstractSimon simon = allSimons.remove(name);
		if (simon.getChildren().size() > 0) {
			replaceSimon(simon, UnknownSimon.class);
		} else {
			((AbstractSimon) simon.getParent()).replaceChild(simon, null);
		}
	}

	@Override
	public void reset() {
		allSimons.clear();
		rootSimon = new UnknownSimon(SimonManager.ROOT_SIMON_NAME);
		allSimons.put(SimonManager.ROOT_SIMON_NAME, rootSimon);
	}

	@Override
	public synchronized Counter getCounter(String name) {
		return (Counter) getOrCreateSimon(name, CounterImpl.class);
	}

	@Override
	public synchronized Stopwatch getStopwatch(String name, Stopwatch.Type impl) {
		return (Stopwatch) getOrCreateSimon(name, impl.getStopwatchClass());
	}

	@Override
	public UnknownSimon getUnknown(String name) {
		return (UnknownSimon) getOrCreateSimon(name, UnknownSimon.class);
	}

	@Override
	public String generateName(String suffix, boolean includeMethodName) {
		StackTraceElement stackElement = Thread.currentThread().getStackTrace()[CLIENT_CODE_STACK_INDEX];
		StringBuilder nameBuilder = new StringBuilder(stackElement.getClassName());
		if (includeMethodName) {
			nameBuilder.append('.').append(stackElement.getMethodName());
		}
		if (suffix != null) {
			nameBuilder.append(suffix);
		}
		return nameBuilder.toString();
	}

	@Override
	public Simon getRootSimon() {
		return rootSimon;
	}

	@Override
	public Collection<String> simonNames() {
		return allSimons.keySet();
	}

	private Simon getOrCreateSimon(String name, Class<? extends AbstractSimon> simonClass) {
		AbstractSimon simon = allSimons.get(name);
		if (simon == null) {
			simon = newSimon(name, simonClass);
		} else if (simon instanceof UnknownSimon) {
			simon = replaceSimon(simon, simonClass);
		} else {
			if (!(simonClass.isInstance(simon))) {
				throw new SimonException("Simon named '" + name + "' already exists and its type is '" + simon.getClass().getName() + "' while requested type is '" + simonClass.getName() + "'.");
			}
		}
		return simon;
	}

	private AbstractSimon replaceSimon(AbstractSimon simon, Class<? extends AbstractSimon> simonClass) {
		AbstractSimon newSimon = instantiateSimon(simon.getName(), simonClass);
		newSimon.enabled = simon.enabled;

		// fixes parent link and parent's children list
		((AbstractSimon) simon.getParent()).replaceChild(simon, newSimon);

		// fixes children list and all children's parent link
		for (Simon child : simon.getChildren()) {
			newSimon.addChild((AbstractSimon) child);
			((AbstractSimon) child).setParent(newSimon);
		}

		allSimons.put(simon.getName(), newSimon);
		return newSimon;
	}

	private AbstractSimon newSimon(String name, Class<? extends AbstractSimon> simonClass) {
		AbstractSimon simon = instantiateSimon(name, simonClass);
		if (name != null) {
			addToHierarchy(simon, name);
		}
		return simon;
	}

	private AbstractSimon instantiateSimon(String name, Class<? extends AbstractSimon> simonClass) {
		AbstractSimon simon;
		try {
			Constructor<? extends AbstractSimon> constructor = simonClass.getDeclaredConstructor(String.class);
			simon = constructor.newInstance(name);
		} catch (NoSuchMethodException e) {
			throw new SimonException(e);
		} catch (InvocationTargetException e) {
			throw new SimonException(e);
		} catch (IllegalAccessException e) {
			throw new SimonException(e);
		} catch (InstantiationException e) {
			throw new SimonException(e);
		}
		return simon;
	}

	private void addToHierarchy(AbstractSimon simon, String name) {
		allSimons.put(name, simon);
		int ix = name.lastIndexOf(SimonManager.HIERARCHY_DELIMITER);
		AbstractSimon parent = rootSimon;
		if (ix != -1) {
			String parentName = name.substring(0, ix);
			parent = allSimons.get(parentName);
			if (parent == null) {
				parent = new UnknownSimon(parentName);
				addToHierarchy(parent, parentName);
			}
		}
		parent.addChild(simon);
	}
}
