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
    private static int[][] obstacleMap; // obstacleMap[x-coord][y-coord]
    private static final int BLOCKED = 0;
    private static final int EMPTY = 1;
    private static final int UNEXPLORED = 2;
    private boolean[][] debugPathGrid = null;
    
    /** A grid where each value represents the probability that an agent is there. */
    private static double[][] agentMap;
    private Pos currentPos = null;

    /** All of the previous moves made */
    private ArrayList<Integer> moveHistory = new ArrayList<>();
    private boolean shouldBeOnHomeCol;
    private boolean[] startSurroundings = null;
    
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
     * the matrices are instantiated.
     */
    
    public TestAgent(){
	ID = nextID++;
	shouldBeOnHomeCol = true;
	initComplete = false;
	southInitComplete = false;
	northInitComplete = false;
	northTravelDist = 0;
	southTravelDist = 0;
	startSurroundings = null;
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

	public int hashCode(){
	    return (x*7+y*3)%97;
	}
	
	public boolean equals(Pos other){
	    return x==other.x && y==other.y;
	}
    }

    private class Surrounding{
	boolean east;
	boolean north;
	boolean west;
	boolean south;

	public Surrounding(boolean east, boolean north, boolean west, boolean south){
	    this.east = east;
	    this.north = north;
	    this.west = west;
	    this.south = south;
	}

	public boolean equals(Surrounding other){
	    // TODO: A really nasty conditional
	    return true;
	}
    }

    private void setInitPos(boolean northAgent){
	if (startSide==LEFT_START) {
	    currentPos = new Pos(0, southTravelDist + (northAgent ? 2 : 0));
	}else{
	    currentPos = new Pos(obstacleMap.length-1, southTravelDist + (northAgent ? 2 : 0));
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
	if(initComplete) agentMap[currentPos.x][currentPos.y] = 0;
	
	switch(m){
	case AgentAction.MOVE_EAST:
	    currentPos.x++;
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
	if(initComplete) agentMap[currentPos.x][currentPos.y] = 1.0;
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

	public int getManhattan(){
	    return manhattan;
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
    private int moveTowards(Pos goal, boolean hasFlag){
	// clear the debugging grid
	if(debug) debugPathGrid = new boolean[obstacleMap.length][obstacleMap.length];
	if(goal==null || goal.equals(currentPos)) return AgentAction.DO_NOTHING;
	//A* search to goal
	PathSearchNode currentNode = new PathSearchNode(AgentAction.DO_NOTHING, 0,
							manhattanDist(currentPos, goal), currentPos, null);
	PriorityQueue<PathSearchNode> heap = new PriorityQueue<>();
	HashMap<Pos,PathSearchNode> searchHistory = new HashMap<>();
	heap.add(currentNode);
	searchHistory.put(currentPos,null);

	int loopCount = 0;
	int loopLimit = obstacleMap.length*obstacleMap.length;
	while(!heap.isEmpty() && loopCount < loopLimit){
	    loopCount++;
	    currentNode = heap.poll();
	    if(debug) System.out.print(currentNode.getManhattan()+" ");
	    if(debug) System.out.println(currentNode.getPos().toString());

	    // goal test 
	    if(currentNode.getPos().equals(goal)){
		//unravel the stack
		while(currentNode.getParent().getParent()!=null){
		    // save the path for printing & debugging
		    currentNode = currentNode.getParent();
		    if(debug) debugPathGrid[currentNode.getPos().x][currentNode.getPos().y] = true;
		}
		if(debug) {
		    
		    System.out.println("\nBest Move: "+moveToString(currentNode.getMove()));
		}
		return currentNode.getMove();
	    }else{
		// expand successors
		int currentX = currentNode.getPos().x;
		int currentY = currentNode.getPos().y;
		Pos[] adjacentCells = {
		    new Pos(currentX+1,currentY ), // east
		    new Pos(currentX-1,currentY ), // west
		    new Pos(currentX,currentY+1 ), // north
		    new Pos(currentX,currentY-1 ) // south
		};
		int[] directions = {
		    AgentAction.MOVE_EAST,
		    AgentAction.MOVE_WEST,
		    AgentAction.MOVE_NORTH,
		    AgentAction.MOVE_SOUTH
		};
		for(int i = 0; i<4; i++){
		    Pos temp = adjacentCells[i];
		    if( ( hasFlag || !temp.equals(homeBase) ) &&
			// HomeBase is an obstacle unless the agent has the enemy flag.
			// Don't add to heap if temp is homeBase unless the goal is homeBase.
			!testAgent(temp) && !(BLOCKED==testObstacle(temp)) &&
			// Don't add to heap if there's a wall or agent in the way
			!searchHistory.containsKey(temp)){
			// or if it's been expanded already
			
			heap.add(new PathSearchNode(directions[i], currentNode.pathCost+1,
						    manhattanDist(temp,goal), temp, currentNode));
			searchHistory.put(temp, null);
		    }
		}
	    }
	}
	if(debug && loopCount==999) System.out.println("\nWARNING: Infinite loop averted.");
	if(debug) System.out.println("\nERROR: Search Failed");
	return AgentAction.DO_NOTHING;
    }

    private void insertObstacle(Pos p, int status){
	try{
	    obstacleMap[p.x][p.y] = status;
	}catch(IndexOutOfBoundsException e){}
    }

    private boolean testAgent(Pos p){
	try{
	    return 1==agentMap[p.x][p.y];
	}catch(IndexOutOfBoundsException e){
	    return true;
	}
    }
    
    private int testObstacle(Pos p){
	try{
	    return obstacleMap[p.x][p.y];
	}catch(IndexOutOfBoundsException e){
	    return BLOCKED;
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

	insertObstacle(new Pos(x+1, y),   right ? BLOCKED : EMPTY);
	insertObstacle(new Pos(x,   y+1), up    ? BLOCKED : EMPTY);
	insertObstacle(new Pos(x-1, y),   left  ? BLOCKED : EMPTY);
	insertObstacle(new Pos(x,   y-1), down  ? BLOCKED : EMPTY);
    }

    private String moveToString(int m){
	switch(m){
	case AgentAction.MOVE_EAST:
	    return "MOVE_EAST";
	case AgentAction.MOVE_NORTH:
	    return "MOVE_NORTH";
	case AgentAction.MOVE_WEST:
	    return "MOVE_WEST";
	case AgentAction.MOVE_SOUTH:
	    return "MOVE_SOUTH";
	case AgentAction.DO_NOTHING:
	    return "DO_NOTHING";
	case PLANT_MINE:
	    return "PLANT_MINE";
	default:
	    return "ERROR: INVALID_MOVE";
	}
    }
    
    private void printMap() {
	int width = obstacleMap.length;
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
		if(obstacleMap[column][row]==BLOCKED){
		    System.out.print("[]");
		}else if(column==currentPos.x && row == currentPos.y){
		    //System.out.printf("%02d",ID);
		    System.out.printf("AA");
		}else if(column==homeBase.x && row == homeBase.y){
		    System.out.print("HB");
		}else if(column==enemyBase.x && row==enemyBase.y){
		    System.out.print("EB");
		}else if(debugPathGrid[column][row]){
		    System.out.print("--");
		}else if(obstacleMap[column][row]==UNEXPLORED){
		    System.out.print("??");
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

    /** checks the surrounding spaces and checks them against the obstacle map */
    private boolean expectedSurroundings(AgentEnvironment e){
	Surrounding s = new Surrounding(e.isObstacleEastImmediate(),
			    e.isObstacleNorthImmediate(),
			    e.isObstacleWestImmediate(),
			    e.isObstacleSouthImmediate());
	    return true;
	// TODO: a really nasty conditional 
	//return testObstacle(new Pos(x+1,y)) == )
    }
    
    private boolean onHomeCol(AgentEnvironment env){
	// Returns true if home base is not to the east or west
	return !( env.isBaseWest(env.OUR_TEAM, false) || env.isBaseEast(env.OUR_TEAM, false) );
    }
    
    
    // implements Agent.getMove() interface
    private void createMaps(int mapWidth){
	obstacleMap = new int[mapWidth][mapWidth];
	for(int i = 0; i< mapWidth; i++){
	    for (int j = 0; j<mapWidth; j++) {
		obstacleMap[i][j] = UNEXPLORED;
	    }
	}
	for(int i = 0; i< mapWidth; i++){
	    obstacleMap[0][i] = EMPTY;
	    obstacleMap[mapWidth-1][i] = EMPTY;
	}
	agentMap = new double[mapWidth][mapWidth];
	debugPathGrid = new boolean[mapWidth][mapWidth];
    }
    
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
		//if(debug) System.out.println("Map width: "+mapWidth);
		createMaps(mapWidth);
				
		/** set the current location */
		setInitPos(true);
		
		/** Set the location of each base for convenience*/
		if(startSide==RIGHT_START){
		    homeBase = new Pos(obstacleMap.length-1,
				       (obstacleMap.length-1)/2);
		    enemyBase = new Pos(0,(obstacleMap.length-1)/2);
		}else{
		    homeBase = new Pos(0,(obstacleMap.length-1)/2);
		    enemyBase = new Pos(obstacleMap.length-1,
					(obstacleMap.length-1)/2);
		}
		
	    }else{
		//return recordMove(AgentAction.DO_NOTHING);
		return AgentAction.DO_NOTHING;
	    }
	
	}
	// Executed by the first agent to reach home base
	if(currentPos==null){
	    setInitPos(env.isBaseSouth(env.OUR_TEAM, false));
	}
	
	/** Check if the agent has been tagged or exploded */
	if((env.isObstacleNorthImmediate() || env.isObstacleSouthImmediate()) && onHomeCol(env)){
	    // TODO: Do what should be done after being tagged/blown up
	    // TODO: Update currentPos
	    // TODO: Test whether somebody scored
	    if(startSide==LEFT_START){
		currentPos.x = 0;
		if(env.isBaseSouth(AgentEnvironment.OUR_TEAM, false)){
		    currentPos.y = obstacleMap.length-1;
		}else{
		    currentPos.y = 0;
		}
	    }else{
		currentPos.x = obstacleMap.length-1;
		if(env.isBaseSouth(AgentEnvironment.OUR_TEAM, false)){
		    currentPos.y = obstacleMap.length-1;
		}else{
		    currentPos.y = 0;
		}
	    }
	} else{
	    updateObstacleMap(env);
	}
	
	int finalMove;
	if(env.isBaseNorth(env.OUR_TEAM,true)){
	    return recordMove(moveTowards(currentPos,false));
	}
	if(env.hasFlag(env.OUR_TEAM)){
	    // B-line for home base
	    finalMove = moveTowards(homeBase,true);
	}else{
	    // B-line for the enemy base
	    finalMove = moveTowards(enemyBase,false);
	}
	if(debug) printMap();	

	
	return recordMove(finalMove);
    }
}
