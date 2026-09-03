package org.fog.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.fog.utils.TimeKeeper;

public class AppLoop {
	private final int loopId;
	private final List<String> modules;

	public AppLoop(List<String> modules) {
		if (modules == null || modules.size() < 2) {
			throw new IllegalArgumentException(
				"An application loop requires at least two modules");
		}
		List<String> validatedModules = new ArrayList<String>(modules.size());
		for (String module : modules) {
			if (module == null || module.trim().isEmpty()) {
				throw new IllegalArgumentException(
					"Application loop module names cannot be blank");
			}
			validatedModules.add(module);
		}
		this.loopId = TimeKeeper.getInstance().getUniqueId();
		this.modules = Collections.unmodifiableList(validatedModules);
	}

	public boolean hasEdge(String src, String dest) {
		for (int i = 0; i < modules.size() - 1; i++) {
			if (modules.get(i).equals(src) && modules.get(i + 1).equals(dest))
				return true;
		}
		return false;
	}

	public String getStartModule() {
		return modules.get(0);
	}

	public String getEndModule() {
		return modules.get(modules.size() - 1);
	}

	public boolean isStartModule(String module) {
		if (getStartModule().equals(module))
			return true;
		return false;
	}

	public boolean isEndModule(String module) {
		if (getEndModule().equals(module))
			return true;
		return false;
	}

	public Optional<String> getNextModuleInLoop(String module) {
		for (int index = 0; index < modules.size() - 1; index++) {
			if (modules.get(index).equals(module)) {
				return Optional.of(modules.get(index + 1));
			}
		}
		return Optional.empty();
	}

	public List<String> getModules() {
		return modules;
	}

	public int getLoopId() {
		return loopId;
	}

}
