package ctf.agent;


import ctf.common.AgentEnvironment;
import ctf.agent.Agent;

import ctf.common.AgentAction;

import java.util.ArrayList;

/**
 * A sample agent implementing the Agent interface.
 * Your class should look similar to this, except it should be called
 * "YourNetIDAgent".
 * <BR>
 * This agent plays in a rather naive fashion.  It has no notion of
 * defense.  It heads towards the enemy flag until it gets the flag,
 * and then it heads back towards its own base.  It can avoid some 
 * obstacles, but still gets stuck permanently in certain situations.
 * It has no notion of its teammates or its enemies, so it doesn't try to 
 * avoid them at all.
 * 
 */
public class TestAgent extends Agent {
    /** a more convenient name for planting a mine */
    private static final int PLANT_MINE = AgentAction.PLANT_HYPERDEADLY_PROXIMITY_MINE;
    
    /** A map shared by both agents  */
    private static boolean[][] globalObstacleMap;
    
    /** A grid where each value represents the probability that an agent is there. */
    private static ArrayList<Double> globalAgentMap;
    private Pos agentPos = null;

    /** All of the previous moves made */
    private ArrayList<Integer> moveHistory = new ArrayList<>();
    private boolean shouldBeOnHomeCol;
    
    /** An id used to differentiate the agents */
    private static int nextID = 0;
    private final int ID;

    /** Boolean used to determine the starting position */
    private final int LEFT_START = 0;
    private final int RIGHT_START =1;
    private static int startSide = -1;

    /** Whethter or not the initial sequence has been completed */
    private static boolean initComplete;
    private static boolean northInitComplete;
    private static boolean southInitComplete;
    private static int northTravelDist, southTravelDist;
    
    /* The initial sequence involves moving toward home base in order to 
     * determine the width of the map. Once the map width is known, 
     * 
     */
    
    public TestAgent(){
	ID = nextID++;
	shouldBeOnHomeCol = true;
	initComplete = false;
	southInitComplete = false;
	northInitComplete = false;
	northTravelDist = 0;
	southTravelDist = 0;
    }

    private class Pos {
	public int x, y;
	
	public Pos(int x, int y){
	    this.x = x;
	    this.y = y;
	}
    }

    private Pos toGlobalPos(Pos agentPos, Pos relativePos){
	return new Pos(relativePos.x+agentPos.x,
		       relativePos.y+agentPos.y);
    }

    private Pos toRelativePos(Pos agentPos, Pos globalPos){
	return new Pos(globalPos.x-agentPos.x,
		       globalPos.y-agentPos.y);
    }

    /** Returns the last move recorded in move history */
    private int lastMove(){
	if(moveHistory.size()==0){
	    return AgentAction.DO_NOTHING;
	}else{
	    return moveHistory.get(moveHistory.size()-1);
	}
    }

    private int recordMove(int m){
	moveHistory.add(m);
	switch(m){
	case AgentAction.MOVE_EAST:
	    if(startSide==LEFT_START){
		
	    }else{
		
	    }
	    break;
	case AgentAction.MOVE_NORTH:
	    break;
	case AgentAction.MOVE_WEST:
	    break;
	case AgentAction.MOVE_SOUTH:
	    break;
	case AgentAction.DO_NOTHING:
	    break;
	case PLANT_MINE:
	    break;
	default:
	    break;
	}
	return m;
    }
    
    
    /**
     * Updates the current map hypothesis
     */
    private void updateMap( AgentEnvironment env){
	// update obstacle map
	boolean obstNorth = env.isObstacleNorthImmediate();
	boolean obstSouth = env.isObstacleSouthImmediate();
	boolean obstEast = env.isObstacleEastImmediate();
	boolean obstWest = env.isObstacleWestImmediate();

	//update locations of enemy agents
	
    }

    private int manhattanDist(Pos p1, Pos p2){
	return (int) Math.abs(p1.x-p2.x)+Math.abs(p1.y-p2.y);
    }
    
    /**
     * Checks the map and calculates the quickest route to the specified base
     * returns the best direction
     *
     */
    private int getBaseMove(AgentEnvironment env, int base){
	return AgentAction.MOVE_NORTH;
    }

    private void printMap() {
	int width = globalObstacleMap.length;
	// print the top of the map
	System.out.print(" ");
	for (int col = 0; col<width; col++) {
	    System.out.print("--");
	}
	System.out.println();
	// print each row of the map
	for (int row = 0; row<width; row++){
	    System.out.print("|");
	    for(int column = 0; column<width; column++){
		if(globalObstacleMap[row][column]){
		    System.out.print("[]");
		}else{
		    System.out.print("  ");
		}
	    }
	    System.out.println("|");
	}
	//print the bottom edge of the map
	System.out.print(" ");
	for (int col = 0; col<width; col++) {
	    System.out.print("--");
	}
	System.out.println();
    }

    private void updateStartSide(AgentEnvironment e){
	if(e.isBaseEast(e.ENEMY_TEAM, false))
	    startSide = LEFT_START;
	else
	    startSide = RIGHT_START;
    }

    private boolean onHomeRow(AgentEnvironment env){
	// Returns true if home base is not to the east or west
	return !( env.isBaseWest(env.OUR_TEAM, false) || env.isBaseEast(env.OUR_TEAM, false) );
    }
    
    
    // implements Agent.getMove() interface
    public int getMove( AgentEnvironment env ) {
	

	/** check whether the teammates have met in the middle */
	if(!initComplete){
	    updateStartSide(env);
	    
	    // NORTH AGENT
	    // If this is the northmost agent
	    if(env.isBaseSouth(env.OUR_TEAM, false)){
		// Travel south if home base is not immediately south
		if(!env.isBaseSouth(env.OUR_TEAM, true)){
		    northTravelDist++;
		    return recordMove(AgentAction.MOVE_SOUTH);
		}else{
		    northInitComplete= true;
		}
		// SOUTH AGENT
		// if this is the southern agent
	    }else{
		// travel north if home base is not immediately north
		if(!env.isBaseNorth(env.OUR_TEAM, true)){
		    southTravelDist++;
		    return recordMove(AgentAction.MOVE_NORTH);
		}else{
		    southInitComplete= true;
		}
	    }
	    if(initComplete = southInitComplete && northInitComplete){
		int mapWidth = northTravelDist+southTravelDist+3;
		//System.out.println("Map width: "+mapWidth);
		globalObstacleMap = new boolean[mapWidth][mapWidth];
	    }else{
		return recordMove(AgentAction.DO_NOTHING);
	    }
	
	}

	
	printMap();

	/** Check if the agent has been tagged or exploded */
	if(!shouldBeOnHomeCol && onHomeCol(env)){
	    // TODO: Do what should be done after being tagged/blown up
	}
        
	// check for immediate obstacles blocking our path		
	
		
		
    }

}
