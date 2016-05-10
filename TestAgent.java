package ctf.agent;


import ctf.common.AgentEnvironment;
import ctf.agent.Agent;

import ctf.common.AgentAction;

import java.awt.Image;
import java.awt.Graphics;
import java.awt.Color;

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
    /** 
     * enables debug prints 
     */
    private static final boolean debug = true; 
    
    /** 
     * a more convenient name for planting a mine 
     */
    private static final int PLANT_MINE = AgentAction.PLANT_HYPERDEADLY_PROXIMITY_MINE;
    
    /** 
     * A map shared by both agents  
     */
    private static int[][] obstacleMap; // obstacleMap[x-coord][y-coord]
    private static final int BLOCKED = 100;
    private static final int EMPTY = 101;
    private static final int UNEXPLORED = 102;
    private boolean[][] debugPathGrid = null;
    
    /** 
     * A hashmap containing the location of all bombs planted
     */
    private static HashMap<Pos, Boolean> bombMap = new HashMap<>();
    
    /** 
     * A grid where each value represents the certainty that an agent is there.
     *
     * +1 indicates a teammate
     * -1 indicates a 100% chance that an enemy is there
     * -0.5 indicates the possibility that an enemy is there
     */
    private static double[][] agentMap;
    private static double[][] pathMap;
    private Pos currentPos = null;
    private Pos prevPos = null;

    /** 
     * All of the previous moves made 
     */
    private ArrayList<Integer> moveHistory = new ArrayList<>();
        
    /** 
     * An id used to differentiate the agents 
     */
    private static int nextID = 0;
    private final int ID;
    private static TestAgent agent1;
    private static TestAgent agent2;
    
    /**
     * Share the intentions with teammates
     */
    private PathSearchNode intention; // contains the path that the agent will follow
    private final int DEFEND= 0;
    private final int ATTACK = 1;
    private final int HUNT = 2;
    private final int FOLLOW = 3;
    private int mode;
    private boolean doneExploring = false;
    
    /** 
     * Boolean used to determine the starting position 
     */
    private final int LEFT_START = 110;
    private final int RIGHT_START =111;
    private static int startSide = -1;

    /** 
     * Store the locations of each base 
     */
    private static Pos homeBase = null;
    private static Pos enemyBase = null;

    /** 
     * Whethter or not the initial sequence has been completed 
     */
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
	initComplete = false;
	southInitComplete = false;
	northInitComplete = false;
	northTravelDist = 0;
	southTravelDist = 0;
	enemyBase = null;
	homeBase = null;
	if(agent1==null){
	    agent1 = this;
	} else{
	    agent2 = this;
	}
	if(debug) System.out.println("TestAgent "+ID+" created!");
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
	private double enemyProb = 0.0;
	private Pos pos; // The position that results from making the move
	private PathSearchNode parentNode;
	
	
	public PathSearchNode(int move, int pathCost,int manhattan, Pos pos, PathSearchNode parent,
			      double enemyProb, boolean avoidEnemies, boolean seekEnemies){
	    this.move = move;
	    this.pathCost = pathCost;
	    this.manhattan = manhattan;
	    this.pos = pos;
	    this.parentNode = parent;
	    // if(avoidEnemies) this.enemyProb = enemyProb;
	    // if(seekEnemies) this.enemyProb = -enemyProb;
	}

	public int getPathCost(){
	    return pathCost;
	}

	public double fCost(){
	    double f = pathCost+manhattan;
	    return f+f*enemyProb;
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
	    double value  = fCost()-node.fCost();
	    if(value==0){
		if(move==AgentAction.MOVE_WEST || move==AgentAction.MOVE_EAST){
		    return -1;
		}else if(node.getMove()==AgentAction.MOVE_WEST || node.getMove()==AgentAction.MOVE_EAST){
		    return 1;
		}else{
		    return 0;
		}
	    }else if(value>0){
		return 1;
	    }else{
		return -1;
	    }
	}
    }

    
    public class Pos {
	public int x, y;
	
	public Pos(int x, int y){
	    this.x = x;
	    this.y = y;
	}

	public String toString(){
	    return "("+x+","+y+")";
	}

	@Override
	public int hashCode(){
	    int hash = 3;
	    hash = hash * 5 + x;
	    hash = hash * 13 + y;
	    return hash;
	}
	
	public boolean equals(Object other){
	    Pos p = (Pos) other;
	    return x == p.x && y == p.y;
	}

	public Pos clone(){
	    return new Pos(x,y);
	}
    }

    
    public boolean equals(Object other){
	TestAgent otherAgent = (TestAgent)other;
	return otherAgent.ID==ID;
    }
            
    private void setInitPos(boolean northAgent){
	if (startSide==LEFT_START) {
	    currentPos = new Pos(0, southTravelDist + (northAgent ? 2 : 0));
	}else{
	    currentPos = new Pos(obstacleMap.length-1, southTravelDist + (northAgent ? 2 : 0));
	}
    }
    
    private Pos toGlobalPos(Pos relativePos){
	return new Pos(relativePos.x+currentPos.x,
		       relativePos.y+currentPos.y);
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
	if(!initComplete){
	    return m;
	}
	prevPos = currentPos.clone();
	agentMap[prevPos.x][prevPos.y] = 0;
	agentMap[currentPos.x][currentPos.y] = -1.0;
	
	switch(m){
	case AgentAction.MOVE_EAST:
		if(!e.isAgentEast(e.OUR_TEAM,true))
	    currentPos.x++;
	    break;
	case AgentAction.MOVE_NORTH:
		if(!e.isAgentNorth(e.OUR_TEAM,true))
	    currentPos.y++;
	    break;
	case AgentAction.MOVE_WEST:
		if(!e.isAgentWest(e.OUR_TEAM,true))
	    currentPos.x--;
	    break;
	case AgentAction.MOVE_SOUTH:
		if(!e.isAgentSouth(e.OUR_TEAM,true))
	    currentPos.y--;
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

    private int manhattanDist(Pos p1, Pos p2){
	return (int) Math.abs(p1.x-p2.x)+Math.abs(p1.y-p2.y);
    }
    
    /** 
     * Using A* search and the current obstacle map of the environment find 
     * a route to the specified base
     * PRECONDITION: the obstacle map has already been updated
     * PRECONDITION: the current position is known
     *
     */
    // TODO: fix pathfinding with possession of flag
    // TODO: Incorporate probability matrices
    private PathSearchNode getPath(Pos start, Pos goal, boolean hasFlag,
				   boolean avoidEnemies, boolean seekEnemies){
	// clear the debugging grid
	if(debug) debugPathGrid = new boolean[obstacleMap.length][obstacleMap.length];
	if(goal==null || goal.equals(start))
	    return new PathSearchNode(AgentAction.DO_NOTHING, 0, 0, start, null, 0.0, false, false);
	//A* search to goal
	PathSearchNode currentNode = new PathSearchNode(AgentAction.DO_NOTHING, 0,
							manhattanDist(start, goal), start, null, 0, false, false);
	PriorityQueue<PathSearchNode> heap = new PriorityQueue<>();
	HashMap<Pos,PathSearchNode> searchHistory = new HashMap<>();
	heap.add(currentNode);
			
	while(!heap.isEmpty()){
	    // select a node to expand
	    currentNode = heap.poll();
	    if(searchHistory.containsKey(currentNode.getPos())){
		continue;
	    }
	    searchHistory.put(currentNode.getPos(), currentNode); // add it to the search history
	    
	    if(debug) debugPathGrid[currentNode.getPos().x][currentNode.getPos().y]=true;
	    // goal test 
	    if(currentNode.getPos().equals(goal)){
		//unravel the stack
		return currentNode;
		
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
		    // HomeBase is an obstacle unless the agent has the enemy flag.
		    // Don't add to heap if temp is homeBase unless the goal is homeBase.
		    if( ( hasFlag || !temp.equals(homeBase) ) &&
			// Don't add to heap if there's a wall or agent in the way
			!testTeammate(temp) /*&& testEnemy(temp)!=-1.0 */ && !isBlocked(temp)) {
			PathSearchNode newNode =
			    new PathSearchNode(directions[i], currentNode.pathCost+1,
					       manhattanDist(temp,goal), temp, currentNode,
					       testPath(temp), avoidEnemies, seekEnemies);
			heap.add(newNode);
		    }
		}
	    }
	}
	if(debug) System.out.println("\nERROR: Search Failed");
	return new PathSearchNode(AgentAction.DO_NOTHING, 0, 0, start, null, 0, false, false);
    }
        
    private int moveTowards(Pos goal, boolean hasFlag,
			    boolean avoidEnemies, boolean seekEnemies){
	PathSearchNode currentNode = getPath(currentPos, goal, hasFlag, avoidEnemies, seekEnemies);
	intention = currentNode;
	while(currentNode.getParent()!=null &&
	      currentNode.getParent().getParent()!=null){
	    // save the path for printing & debugging
	    currentNode = currentNode.getParent();
	    if(debug) debugPathGrid[currentNode.getPos().x][currentNode.getPos().y] = true;
	}
	if(debug) {
	    System.out.println("\nBest Move: "+moveToString(currentNode.getMove()));
	}
	
	return currentNode.getMove();
    }

    private int pathLength(PathSearchNode n){
	int length  = 0;
	PathSearchNode currentNode = n;
	while(currentNode.getParent()!=null){
	    length++;
	    currentNode = currentNode.getParent();
	}
	return length;
    }

    private void insertObstacle(Pos p, int status){
	try{
	    obstacleMap[p.x][p.y] = status;
	}catch(IndexOutOfBoundsException e){}
    }

    private void insertAgent(Pos p, double status){
	try{
	    agentMap[p.x][p.y] = status;
	}catch(IndexOutOfBoundsException e){}
    }

    private void insertBomb(Pos p){
	bombMap.put(p, true);
    }

    private void removeBomb(Pos p){
	bombMap.remove(p);
    }
    
    
    private boolean testTeammate(Pos p){
	try{
	    return -1.0==agentMap[p.x][p.y];
	}catch(IndexOutOfBoundsException e){
	    return false;
	}
    }

    private double testEnemy(Pos p){
	try{
	    return agentMap[p.x][p.y];
	}catch(IndexOutOfBoundsException e){
	    return 0.0;
	}
    }

    private double testPath(Pos p){
	try{
	    return pathMap[p.x][p.y];
	}catch(IndexOutOfBoundsException e){
	    return 0.0;
	}
    }

    private boolean isBlocked(Pos p){
	try{
	    return obstacleMap[p.x][p.y]==BLOCKED;
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

    
    private void moveEnemies(){
	if(debug) System.out.println("Moving enemies...");
	double oldAgentMap[][] = agentMap.clone();
	agentMap = new double[agentMap.length][agentMap.length];
	for(int x = 0; x < agentMap.length; x++){
	    for(int y = 0; y < agentMap[x].length; y++){
		if(oldAgentMap[x][y]>0){
		    Pos p0 = new Pos(x,y);
		    Pos p1 = new Pos(x+1, y);
		    Pos p2 = new Pos(x-1, y);
		    Pos p3 = new Pos(x, y+1);
		    Pos p4 = new Pos(x, y-1);
		    insertAgent(p0, isBlocked(p1) ? 0.0 : 0.5);
		    insertAgent(p1, isBlocked(p1) ? 0.0 : 0.5);
		    insertAgent(p2, isBlocked(p2) ? 0.0 : 0.5);
		    insertAgent(p3, isBlocked(p3) ? 0.0 : 0.5);
		    insertAgent(p4, isBlocked(p4) ? 0.0 : 0.5);
		}else if(oldAgentMap==-1){
		    agentMap[x][y] = -1;
		}
		
	    }
	}
    }

    /** 
     * Precondition: Enemy positions have been marked by a 1
     * possible positions have been marked with 0.5
     */
    private void normalizeProbs(){
	if(debug) System.out.println("Normalizing probs...");
	int count = 0;
	for(int x = 0; x < agentMap.length; x++){
	    for(int y = 0; y < agentMap[x].length; y++){
		if(agentMap[x][y]>0 && agentMap[x][y] < 1){
		    count++;
		}
	    }
	}
	double val = 1/(double)count;
	for(int x = 0; x < agentMap.length; x++){
	    for(int y = 0; y < agentMap[x].length; y++){
		if(agentMap[x][y]>0 && agentMap[x][y]<1){
		    agentMap[x][y] = val;
		}
	    }
	}
    }
    
    private void updateMaps(AgentEnvironment e){
	if(debug) System.out.println("Updating Maps...");
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

	// Update immediate agent positions
	boolean agentRight = e.isAgentEast(e.ENEMY_TEAM, true);
	boolean agentUp = e.isAgentNorth(e.ENEMY_TEAM, true);
	boolean agentLeft = e.isAgentWest(e.ENEMY_TEAM, true);
	boolean agentDown = e.isAgentSouth(e.ENEMY_TEAM, true);

	boolean[] agents = {agentRight, agentUp, agentLeft, agentDown};
	int sum = 0;
	for (int i = 0; i < agents.length; i++) {
	    sum+= agents[i] ? 1 : 0;
	}
	// We know for sure where both of the enemy agents are
	// THEY'RE RIGHT NEXT TO US!!!!
	if(sum==2){
	    // clear the map of .5's and -1's
	    for(int row = 0; row < agentMap.length; row++){
		for(int col = 0; col<agentMap[row].length; col++){
		    if(agentMap[col][row]!=1){
			agentMap[col][row] = 0;
		    }
		}
	    }
	    if(agentRight)
		insertAgent(new Pos(x+1,y), 1);
	    if(agentLeft)
		insertAgent(new Pos(x-1,y), 1);
	    if(agentUp)
		insertAgent(new Pos(x,y+1), 1);
	    if(agentDown)
		insertAgent(new Pos(x,y-1), 1);
	}else{
	    moveEnemies();
	    // if(debug) printAgentMap();
	}
	
	boolean rightFar = e.isAgentEast(e.ENEMY_TEAM, false);
	boolean upFar= e.isAgentNorth(e.ENEMY_TEAM, false);
	boolean leftFar = e.isAgentWest(e.ENEMY_TEAM, false);
	boolean downFar = e.isAgentSouth(e.ENEMY_TEAM, false);	    
	if(sum==1){
	    if(agentRight){
		insertAgent(new Pos(x+1,y), 1);
	    }else if(agentLeft){
		insertAgent(new Pos(x-1,y), 1);
	    }else if(agentUp){
		insertAgent(new Pos(x,y+1), 1);
	    }else if(agentDown){
		insertAgent(new Pos(x,y-1), 1);
	    }
	    if(!rightFar){
		removeEastAgents();
	    }
	    if(!upFar){
		removeNorthAgents();
	    }
	    if(!leftFar){
		removeWestAgents();
	    }
	    if(!downFar){
		removeSouthAgents();
	    }
	}
	if(sum==0){
	    if(!rightFar){
		// mark all right positions with a 0
		removeEastAgents();    
	    }
	    if(!upFar){
		
		removeNorthAgents();    
	
	    }
	    if(!leftFar){
		removeWestAgents();
	    }
	    if(!downFar){
		removeSouthAgents();
	    }
	    if(rightFar && leftFar){
		// mark the current column with all zeros
		for(int row = 0; row<agentMap.length; row++){
		    agentMap[currentPos.x][row] = 0;
		}
	    }
	    if(upFar && downFar){
		// mark the current row with all zeros
		for(int col = 0; col<agentMap.length; col++){
		    agentMap[col][currentPos.y] = 0;
		}
	    }
	}
	normalizeProbs();
    }

    private void removeSouthAgents(){
	for(int col = 0; col<agentMap.length; col++){
		for(int row = currentPos.y-1; row>0; row--){
		    insertAgent(new Pos(col,row), 0.0);
		}
	    }
    }

    private void removeNorthAgents(){
	for(int col = 0; col<agentMap.length; col++){
		for(int row = currentPos.y+1; row<agentMap[col].length; row++){
		    insertAgent(new Pos(col,row), 0.0);
		}
	    }
    }

    private void removeEastAgents(){
	for(int col = currentPos.x+1; col<agentMap.length; col++){
	    for(int row = 0; row<agentMap[col].length; row++){
		insertAgent(new Pos(col,row), 0.0);
	    }
	}	
    }

    private void removeWestAgents(){
	for(int col = currentPos.x-1; col>0; col--){
		for(int row = 0; row<agentMap[col].length; row++){
		    agentMap[col][row]= 0.0;
		}
	    }
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
	    return "ERROR: INVALID MOVE: "+m;
	}
    }
    
    private void printObstacleMap() {
	int width = obstacleMap.length;
	// print the top of the map
	System.out.print("[]");
	for (int col = 0; col<=width; col++) {
	    System.out.print("[]");
	}
	System.out.println();
	// print each row of the map
	for (int row = width-1; row>=0; row--){
	    System.out.print("[]");
	    for(int column = 0; column<width; column++){
		if(obstacleMap[column][row]==BLOCKED){
		    System.out.print("[]");
		}else if(column==currentPos.x && row == currentPos.y){
		    //System.out.printf("%02d",ID);
		    System.out.printf("AA");
		}else if(debugPathGrid!=null && debugPathGrid[column][row]){
		    System.out.print("--");
		}else if(column==homeBase.x && row == homeBase.y){
		    System.out.print("HB");
		}else if(column==enemyBase.x && row==enemyBase.y){
		    System.out.print("EB");
		}else if(obstacleMap[column][row]==UNEXPLORED){
		    System.out.print("??");
		}else{
		    System.out.print("  ");
		}
	    }
	    System.out.println("[]");
	}
	//print the bottom edge of the map
	System.out.print("[]");
	for (int col = 0; col<=width; col++) {
	    System.out.print("[]");
	}
	System.out.println();
    }

    private void printAgentMap() {
	int width = obstacleMap.length;
	// print the top of the map
	System.out.print("[] ");
	for (int col = 0; col<=width; col++) {
	    System.out.print(" []  ");
	}
	System.out.println();
	// print each row of the map
	for (int row = width-1; row>=0; row--){
	    System.out.print("[] ");
	    for(int column = 0; column<width; column++){
		double prob = agentMap[column][row];
		if(obstacleMap[column][row]==BLOCKED){
		    System.out.print(" []  ");
		} else if(prob==1.0){
		    System.out.print(" EE  ");
		} else if(prob==0.0){
		    System.out.print("     ");
		}else if(prob==-1.0){
		    System.out.print(" AA  ");
		} else{
		    System.out.printf("%.2f ", prob);
		}		    
		
	    }
	    System.out.println(" []");
	}
	//print the bottom edge of the map
	System.out.print("[] ");
	for (int col = 0; col<=width; col++) {
	    System.out.print(" []  ");
	}
	System.out.println();
    }

    private void printPathMap(){
	int width = pathMap.length;
	// print the top of the map
	System.out.print(" ");
	for (int col = 0; col<width; col++) {
	    System.out.print("----");
	}
	System.out.println();
	// print each row of the map
	for (int row = width-1; row>=0; row--){
	    System.out.print("| ");
	    for(int column = 0; column<width; column++){
		if(obstacleMap[column][row]==BLOCKED){
		    System.out.print("[ ] ");
		}else{
		    System.out.printf("%3.1f ", pathMap[column][row]);
		}
	    }
	    System.out.println("|");
	}
	//print the bottom edge of the map
	System.out.print(" ");
	for (int col = 0; col<width; col++) {
	    System.out.print("----");
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
	int enemyX = startSide==LEFT_START ? mapWidth-1 : 0;
	insertAgent(new Pos(enemyX, 0), 1);
	insertAgent(new Pos(enemyX, mapWidth-1), 1);
	for(int i = 0; i<=moveHistory.size(); i++){
	    moveEnemies();
	}
	if(debug) printAgentMap();
    }

    private void predictPaths(Pos destination){
	pathMap = new double[agentMap.length][agentMap.length];
	for(int x = 0; x<agentMap.length; x++){
	    for(int y = 0; y<agentMap[x].length; y++){
		Pos start = new Pos(x,y);
		if(testEnemy(start) != 0){
		    PathSearchNode node = getPath(start, destination, true, false, false);
		    while(node.getParent()!=null){
			Pos p = node.getPos();
			// protect from div by 0 by adding 1 to path cost
			pathMap[p.x][p.y] += testEnemy(start)/(1 + node.getPathCost());
			node = node.getParent();
		    }
		}			   
	    }
	}
    }

    private Pos bestChoke(){
	if(debug) System.out.println("Calculating chokepoint...");
	Pos bestPos = currentPos;
	double max = 0;
	for(int x = 0; x<pathMap.length; x++){
	    for(int y = 0; y<pathMap.length; y++){
		Pos temp = new Pos(x,y);
		if(temp.equals(homeBase)){
		    continue;
		}
		double sum = testPath(temp)
		    + testPath(new Pos(temp.x+1, temp.y))
		    + testPath(new Pos(temp.x-1, temp.y))
		    + testPath(new Pos(temp.x, temp.y+1))
		    + testPath(new Pos(temp.x, temp.y-1));
		if(max < sum){
		    max = sum;
		    bestPos = temp;
		}
	    }
	}
	return bestPos;
    }
        
    public void setMode(AgentEnvironment e){
	if(e.hasFlag()){
	    mode = ATTACK;
	}else if(e.hasFlag(e.OUR_TEAM)){
	    mode = DEFEND;
	}else if(e.hasFlag(e.ENEMY_TEAM)){
	    mode = DEFEND;
	} else{
	    TestAgent teammate = (this.equals(agent1)) ? agent2 : agent1;
	    if(teammate.currentPos==null){
		agent1.mode = ATTACK;
		agent2.mode = DEFEND;
		return;
	    }
	    int myDist = this.pathLength(this.getPath(currentPos, enemyBase, false, false, false));
	    int otherDist =
		teammate.pathLength(teammate.getPath(teammate.currentPos, enemyBase, false, false, false));
	    if(myDist==otherDist){
		if(debug) System.out.println(myDist+"=="+otherDist);
		agent1.mode = ATTACK;
		agent2.mode = DEFEND;
	    }else{
		if(debug) System.out.println(myDist+"<"+otherDist);
		mode = (myDist<otherDist) ? ATTACK : DEFEND;
	    }
	}
	
    }
    
    public int attackModeMove(boolean hasFlag){
	predictPaths(currentPos);
	System.out.println("Current Position: "+ currentPos.x+", "+currentPos.y);
	if(hasFlag){
		System.out.println("GOAL: "+homeBase);
	    return moveTowards(homeBase,true, true, false);    

	}else{
		System.out.println("GOAL: "+enemyBase);
	    return moveTowards(enemyBase,false, true, false);			
	    // if(isW1Chokepoint(currentPos) && (Math.random() > 0.925)){
	    // 	return PLANT_MINE;
	    // }else{
	    // 	return moveTowards(enemyBase,false);			
	    // }
	}
    }

    /**
     * Returns the best chokepoint to guard.
     * PRECONDITION: the chokepoints have been weigthed
     */
    
    public int defenseModeMove(boolean enemyHasFlag){
	Pos target = enemyHasFlag ? enemyBase : homeBase;
	predictPaths(target);
	// find a chokepoint to guard
	Pos bestChoke = bestChoke();	    
	// move towards that chokepoint
	return moveTowards(bestChoke, false, false, true);// if we're in defense mode then we don't have the flag
    }

    public int getMove( AgentEnvironment env ) {
	if (debug) System.out.println("***** Processing Agent "+ID+" *****");


	/** check whether the teammates have met in the middle */
	if(!initComplete){
	    updateStartSide(env);
	    
	    // NORTH AGENT
	    // If this is the northmost agent
	    if(env.isBaseSouth(env.OUR_TEAM, false)){
		// set mode to attack
		mode = ATTACK;
		// Travel south if home base is not immediately south
		if(!env.isBaseSouth(env.OUR_TEAM, true)){
		    northTravelDist++;
		    //return recordMove(AgentAction.MOVE_SOUTH);
		    return recordMove(AgentAction.MOVE_SOUTH);
		}else{
		    northInitComplete= true;
		}
		// SOUTH AGENT
		// if this is the southern agent
	    }else{
		// set mode to defense
		mode = DEFEND;
		// travel north if home base is not immediately north
		if(!env.isBaseNorth(env.OUR_TEAM, true)){
		    southTravelDist++;
		    //return recordMove(AgentAction.MOVE_NORTH);
		    return recordMove(AgentAction.MOVE_NORTH);
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
		return recordMove(AgentAction.DO_NOTHING);
	    }
	
	}
	// Executed by the first agent to reach home base
	if(currentPos==null){
	    setInitPos(env.isBaseSouth(env.OUR_TEAM, false));
	}
	
	/** Check if the agent has been tagged or exploded */
	if((env.isObstacleNorthImmediate() || env.isObstacleSouthImmediate()) && onHomeCol(env)){
	    prevPos = currentPos.clone();
	    agentMap[prevPos.x][prevPos.y] = 0;
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
	}
	updateMaps(env);
	
	/** Separate responsibilities*/
	int finalMove = 0;
	setMode(env);
	switch(mode){
	case ATTACK:
	    if(debug) System.out.println("ATTACK MODE");
	    finalMove = attackModeMove(env.hasFlag());
	    break;
	case DEFEND:
	    if(debug) System.out.println("DEFEND MODE");
	    finalMove = defenseModeMove(env.hasFlag(env.ENEMY_TEAM));
	    break;
	case HUNT:
	    break;
	case FOLLOW:
	    break;
	default:
	}    

	if(debug) {
	    printObstacleMap();
	    printAgentMap();
	    //printPathMap();
	}

	
	return recordMove(finalMove);
    }

    public void drawIcon(Graphics g, int width, int height){
	// draw a circle lol
	int xCenter = width/2;
	int yCenter = height/2;
	g.setColor(Color.blue);
	g.fillOval(0,0,width-1,height-1);
    }
}
