package ctf.agent;


import ctf.common.AgentEnvironment;
import ctf.agent.Agent;

import ctf.common.AgentAction;

/**
 * A sample agent that makes completely random moves and doesn't 
 * examine environment at all.
 * 
 */
public class DoNothing extends Agent {
	
    // implements Agent.getMove() interface
    public int getMove( AgentEnvironment inEnvironment ) {
	return AgentAction.DO_NOTHING;
    }
}
