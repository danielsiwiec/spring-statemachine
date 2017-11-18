/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package demo.datajpapersist;

import java.util.EnumSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.access.StateMachineAccess;
import org.springframework.statemachine.access.StateMachineFunction;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import demo.datajpapersist.StateMachineConfig.Events;
import demo.datajpapersist.StateMachineConfig.States;

@Controller
public class StateMachineController {

	public final static String MACHINE_ID_1 = "datajpapersist1";
	public final static String MACHINE_ID_2 = "datajpapersist2";
	private final static String[] MACHINES = new String[] { MACHINE_ID_1, MACHINE_ID_2 };

	@Autowired
	private StateMachineFactory<States, Events> stateMachineFactory;

	@Autowired
	private StateMachinePersist<States, Events, String> stateMachinePersist;

	private StateMachine<States, Events> cachedStateMachine;
	private final StateMachineLogListener listener = new StateMachineLogListener();

	@RequestMapping("/")
	public String home() {
		return "redirect:/state";
	}

	@RequestMapping("/state")
	public String feedAndGetStates(
			@RequestParam(value = "events", required = false) List<Events> events,
			@RequestParam(value = "machine", required = false, defaultValue = MACHINE_ID_1) String machine,
			Model model) throws Exception {

		StateMachine<States, Events> stateMachine = getStateMachine(machine);
		if (events != null) {
			for (Events event : events) {
				stateMachine.sendEvent(event);
			}
		}
		StateMachineContext<States, Events> stateMachineContext = stateMachinePersist.read(machine);
		model.addAttribute("allMachines", MACHINES);
		model.addAttribute("machine", machine);
		model.addAttribute("allEvents", getEvents());
		model.addAttribute("messages", createMessages(listener.getMessages()));
		model.addAttribute("context", stateMachineContext != null ? stateMachineContext.toString() : "");
		return "states";
	}

	private synchronized StateMachine<States, Events> getStateMachine(String machineId) throws Exception {
		if (cachedStateMachine == null) {
			cachedStateMachine = buildStateMachine(machineId);
			cachedStateMachine.start();
		} else {
			if (!ObjectUtils.nullSafeEquals(cachedStateMachine.getId(), machineId)) {
				cachedStateMachine.stop();
				cachedStateMachine = buildStateMachine(machineId);
				cachedStateMachine.start();
			}
		}
		return cachedStateMachine;
	}

	private StateMachine<States, Events> buildStateMachine(String machineId) throws Exception {
		StateMachine<States, Events> stateMachine = stateMachineFactory.getStateMachine(machineId);
		stateMachine.addStateListener(listener);
		listener.resetMessages();
		return restoreStateMachine(stateMachine, stateMachinePersist.read(machineId));
	}

	private StateMachine<States, Events> restoreStateMachine(StateMachine<States, Events> stateMachine,
			StateMachineContext<States, Events> stateMachineContext) {
		if (stateMachineContext == null) {
			return stateMachine;
		}
		stateMachine.stop();
		stateMachine.getStateMachineAccessor().doWithAllRegions(new StateMachineFunction<StateMachineAccess<States, Events>>() {

			@Override
			public void apply(StateMachineAccess<States, Events> function) {
				function.resetStateMachine(stateMachineContext);
			}
		});
		return stateMachine;
	}

	private Events[] getEvents() {
		return EnumSet.allOf(Events.class).toArray(new Events[0]);
	}

	private String createMessages(List<String> messages) {
		StringBuilder buf = new StringBuilder();
		for (String message : messages) {
			buf.append(message);
			buf.append("\n");
		}
		return buf.toString();
	}
}
