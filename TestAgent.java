package ctf.agent;


import ctf.common.AgentEnvironment;
import ctf.agent.Agent;

import ctf.common.AgentAction;

import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.HashMap;

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
    /** enables debug prints */
    private static final boolean debug = true; 
    
    /** a more convenient name for planting a mine */
    private static final int PLANT_MINE = AgentAction.PLANT_HYPERDEADLY_PROXIMITY_MINE;
    
    /** A map shared by both agents  */
    private static boolean[][] globalObstacleMap; // globalObstacleMap[x-coord][y-coord]
    
    /** A grid where each value represents the probability that an agent is there. */
    private static double[][] globalAgentMap;
    private Pos currentPos = null;

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

    /** Store the locations of each base */
    private static Pos homeBase = null;
    private static Pos enemyBase = null;

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
	enemyBase = null;
	homeBase = null;
	System.out.println("TestAgent "+ID+" created!");
    }

    private class Pos {
	public int x, y;
	
	public Pos(int x, int y){
	    this.x = x;
	    this.y = y;
	}

	public String toString(){
	    return "("+x+","+y+")";
	}
	
	public boolean equals(Pos other){
	    return x==other.x && y==other.y;
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
	if(initComplete) globalAgentMap[currentPos.x][currentPos.y] = 0;
	
	switch(m){
	case AgentAction.MOVE_EAST:
	    currentPos.x++;
	    // set shouldBeOnHomeCol
	    if(startSide==LEFT_START){
		
	    }else{
		
	    }
	    break;
	case AgentAction.MOVE_NORTH:
	    currentPos.y++;
	    break;
	case AgentAction.MOVE_WEST:
	    currentPos.x--;
	    break;
	case AgentAction.MOVE_SOUTH:
	    currentPos.y--;
	    break;
	case AgentAction.DO_NOTHING:
	    break;
	case PLANT_MINE:
	    break;
	default:
	    break;
	}
	if(initComplete) globalAgentMap[currentPos.x][currentPos.y] = 1.0;
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

    private class PathSearchNode implements Comparable<PathSearchNode>{
	private int move;
	private int pathCost;
	private int manhattan;
	private Pos pos; // The position that results from making the move
	private PathSearchNode parentNode;
	
	public PathSearchNode(int move, int pathCost,int manhattan, Pos pos, PathSearchNode parent){
	    this.move = move;
	    this.pathCost = pathCost;
	    this.manhattan = manhattan;
	    this.pos = pos;
	    this.parentNode = parent;
	}

	public int getPathCost(){
	    return pathCost;
	}

	public int fCost(){
	    return pathCost+manhattan;
	}
	
	public int getMove(){
	    return move;
	}

	public Pos getPos(){
	    return pos;
	}

	public PathSearchNode getParent(){
	    return parentNode;
	}

	public int compareTo(PathSearchNode node){
	    return fCost()-node.fCost();
	}
    }

    /** 
     * Using A* search and the current obstacle map of the environment find 
     * a route to the specified base
     * PRECONDITION: the obstacle map has already been updated
     * PRECONDITION: the current position is known
     *
     */
    // TODO: Incorporate probability matrices
    private int moveTowards(Pos goal){
	if(goal==null || goal.equals(currentPos)) return AgentAction.DO_NOTHING;
	//A* search to goal
	PathSearchNode currentNode = new PathSearchNode(AgentAction.DO_NOTHING, 0,
							0, currentPos, null);
	PriorityQueue<PathSearchNode> heap = new PriorityQueue<>();
	HashMap<Pos,PathSearchNode> searchHistory = new HashMap<>();
	heap.add(currentNode);
	searchHistory.put(currentPos,null);
	
	while(!heap.isEmpty()){
	    currentNode = heap.poll();
	    // goal test 
	    if(currentNode.getPos().equals(goal)){
		//unravel the stack
		while(currentNode.getParent().getParent()!=null){
		    currentNode = currentNode.getParent();
		}
		if(debug) System.out.println("Best Move: "+currentNode.getMove());

		return currentNode.getMove();
	    }else{
		// expand successors
		int currentX = currentNode.getPos().x;
		int currentY = currentNode.getPos().y;
		Pos eastPos = new Pos(currentX+1,currentY );
		Pos westPos = new Pos(currentX-1,currentY );
		Pos northPos = new Pos(currentX,currentY+1 );
		Pos southPos = new Pos(currentX,currentY-1 );

		if(!testObstacle(eastPos) && !searchHistory.containsKey(eastPos)){
		    heap.add(new PathSearchNode(AgentAction.MOVE_EAST, currentNode.pathCost+1,
						manhattanDist(eastPos,goal), eastPos, currentNode));
		    searchHistory.put(eastPos,null);
		}
		if(!testObstacle(westPos) && !searchHistory.containsKey(westPos)){
		    heap.add(new PathSearchNode(AgentAction.MOVE_WEST, currentNode.pathCost+1,
						manhattanDist(westPos,goal), westPos, currentNode));
		    searchHistory.put(westPos,null);
		}
		if(!testObstacle(northPos) && !searchHistory.containsKey(northPos)){
		    heap.add(new PathSearchNode(AgentAction.MOVE_NORTH, currentNode.pathCost+1,
						manhattanDist(northPos,goal), northPos, currentNode));
		    searchHistory.put(northPos,null);
		}
		if(!testObstacle(southPos) && !searchHistory.containsKey(southPos)){
		    heap.add(new PathSearchNode(AgentAction.MOVE_SOUTH, currentNode.pathCost+1,
						manhattanDist(southPos,goal), southPos, currentNode));
		    searchHistory.put(southPos,null);
		}
	    }
	}
	if(debug) System.out.println("Search Failed");
	return AgentAction.DO_NOTHING;
    }

    private void insertObstacle(Pos p){
	try{
	    globalObstacleMap[p.x][p.y] = true;
	}catch(IndexOutOfBoundsException e){}
    }

    private boolean testObstacle(Pos p){
	try{
	    return globalObstacleMap[p.x][p.y];
	}catch(IndexOutOfBoundsException e){
	    return true;
	}
    }

    private void updateObstacleMap(AgentEnvironment e){
	boolean right = e.isObstacleEastImmediate();
	boolean up = e.isObstacleNorthImmediate();
	boolean left = e.isObstacleWestImmediate();
	boolean down = e.isObstacleSouthImmediate();
	if(debug && currentPos==null) System.out.println("ERROR: currentPos==null");

	int x = currentPos.x;
	int y = currentPos.y;
	    
	if(right){
	    insertObstacle(new Pos(x+1,y));
	}
	if(up){
	    insertObstacle(new Pos(x,y+1));
	}
	if(left){
	    insertObstacle(new Pos(x-1,y));
	}
	if(down){
	    insertObstacle(new Pos(x,y-1));
	}
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
	for (int row = width-1; row>=0; row--){
	    System.out.print("|");
	    for(int column = 0; column<width; column++){
		if(globalObstacleMap[column][row]){
		    System.out.print("[]");
		}else if(column==currentPos.x && row == currentPos.y){
		    System.out.print("A"+ID);
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

    private boolean onHomeCol(AgentEnvironment env){
	// Returns true if home base is not to the east or west
	return !( env.isBaseWest(env.OUR_TEAM, false) || env.isBaseEast(env.OUR_TEAM, false) );
    }
    
    
    // implements Agent.getMove() interface
    public int getMove( AgentEnvironment env ) {
	if (debug) System.out.println("***** Processing Agent "+ID+" *****");


	/** check whether the teammates have met in the middle */
	if(!initComplete){
	    updateStartSide(env);
	    
	    // NORTH AGENT
	    // If this is the northmost agent
	    if(env.isBaseSouth(env.OUR_TEAM, false)){
		// Travel south if home base is not immediately south
		if(!env.isBaseSouth(env.OUR_TEAM, true)){
		    northTravelDist++;
		    //return recordMove(AgentAction.MOVE_SOUTH);
		    return AgentAction.MOVE_SOUTH;
		}else{
		    northInitComplete= true;
		}
		// SOUTH AGENT
		// if this is the southern agent
	    }else{
		// travel north if home base is not immediately north
		if(!env.isBaseNorth(env.OUR_TEAM, true)){
		    southTravelDist++;
		    //return recordMove(AgentAction.MOVE_NORTH);
		    return AgentAction.MOVE_NORTH;
		}else{
		    southInitComplete= true;
		}
	    }
	    // Executed by the last agent to get to home base
	    if(initComplete = southInitComplete && northInitComplete){
		int mapWidth = northTravelDist+southTravelDist+3;
		if(debug) System.out.println("Map width: "+mapWidth);
		globalObstacleMap = new boolean[mapWidth][mapWidth];
		globalAgentMap = new double[mapWidth][mapWidth];
		
		/** set the current location */
		if (startSide==LEFT_START) {
		    if(env.isBaseSouth(env.OUR_TEAM, false)){
			currentPos = new Pos(0,northTravelDist);
		    }else{
			currentPos = new Pos(0,southTravelDist);
		    }
		}else{
		    if(env.isBaseSouth(env.OUR_TEAM, false)){
			currentPos = new Pos(mapWidth-1,mapWidth-1-northTravelDist);
		    }else{
			currentPos = new Pos(mapWidth-1,southTravelDist);
		    }
		}
		/** Set the location of each base for convenience*/
		if(startSide==RIGHT_START){
		    homeBase = new Pos(globalObstacleMap.length-1,
				       (globalObstacleMap.length-1)/2);
		    enemyBase = new Pos(0,(globalObstacleMap.length-1)/2);
		}else{
		    homeBase = new Pos(0,(globalObstacleMap.length-1)/2);
		    enemyBase = new Pos(globalObstacleMap.length-1,
					(globalObstacleMap.length-1)/2);
		}
		
	    }else{
		//return recordMove(AgentAction.DO_NOTHING);
		return AgentAction.DO_NOTHING;
	    }
	
	}
	// Executed by the first agent to reach home base
	if(currentPos==null){
	    if (startSide==LEFT_START) {
		if(env.isBaseSouth(env.OUR_TEAM, false)){
		    currentPos = new Pos(0,northTravelDist);
		}else{
		    currentPos = new Pos(0,southTravelDist);
		}
	    }else{
		if(env.isBaseSouth(env.OUR_TEAM, false)){
		    currentPos = new Pos(globalObstacleMap.length-1,globalObstacleMap.length-1-northTravelDist);
		}else{
		    currentPos = new Pos(globalObstacleMap.length-1,southTravelDist);
		}
	    }
	}
	
	/** Check if the agent has been tagged or exploded */
	if(!shouldBeOnHomeCol && onHomeCol(env)){
	    // TODO: Do what should be done after being tagged/blown up
	    // TODO: Update currentPos
	    if(startSide==LEFT_START){
		if(env.isBaseSouth(AgentEnvironment.OUR_TEAM, false)){
		    
		}else{
		    
		}
	    }else{
		
	    }
	}
	
	//if(debug) System.out.println("currentPos: "+currentPos.toString());
	updateObstacleMap(env);
	if(debug) printMap();

	if(env.isBaseNorth(env.OUR_TEAM,true)){
	    return recordMove(AgentAction.DO_NOTHING);
	}
	
	if(env.hasFlag(env.OUR_TEAM)){
	    // B-line for home base
	    return recordMove(moveTowards(homeBase));
	}else{
	    // B-line for the enemy base
	    return recordMove(moveTowards(enemyBase));
	}
	
	//return recordMove(AgentAction.DO_NOTHING);
    }
}
