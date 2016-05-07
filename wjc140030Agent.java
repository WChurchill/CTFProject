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
import java.util.ListIterator;
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
public class wjc140030Agent extends Agent {
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
	private LocalMap localMap = new LocalMap();
	private static final int BLOCKED = 100;
	private static final int EMPTY = 101;
	private static final int UNEXPLORED = 102;
	private boolean[][] debugPathGrid = null;

	/** 
	 * A map of key strategic chokepoints 
	 */
	private static int[][] chokepointMap;
	private static int[][] chokeWeightMap;
	private final int TOPLEFT = -1;
	private final int TOPRIGHT = -2;
	private final int VERTICAL = -3;
	private final int HORIZONTAL = -4;
	private final int CROSS = -5;
	private final int DIAG = -6;
	private static final int CHOKEPOINT_1 = 103; // indicates a chokepoint 1 square wide
	private static final int CHOKEPOINT_2 = 104; // indicates a chokepoint 2 squares wide
	private static final int CHOKEPOINT_3 = 105; // indicates a chokepoint of width 3
	private static final int NOT_CHOKEPOINT = 106; // indicates no strategic value for this square
	
	/** 
	 * A map of corridors, generated from the chokepoint map
	 */
	private static int[][] corridorMap;
	private enum direction {horiz, vert, topLeft, topRight};
	
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
	private ArrayList<ArrayList<Double>> agentProbMap= new ArrayList<ArrayList<Double>>();
	private static int mapWidth = -1;
	private boolean touchedBase;
	private Pos localPos = new Pos(0, 0);
	private Pos globalPos = null;
	// the coordinates of the agent last turn
	private Pos prevPos = null;

	/** 
	 * All of the previous moves made 
	 */
	private ArrayList<Integer> moveHistory = new ArrayList<Integer>();
		
	/** 
	 * An id used to differentiate the agents 
	 */
	private static int nextID = 0;
	private final int ID;
	private static wjc140030Agent agent1;
	private static wjc140030Agent agent2;
	
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
	private int START_CORNER;
	// outer arraylist is +x
	// inner arraylist is -y
	private final int NORTH_WEST_START = 0;
	// outer arraylist is -x
	// inner arraylist is -y
	private final int NORTH_EAST_START = 1;
	// Normal coordinate system
	// outer is +x
	// inner is +y
	private final int SOUTH_WEST_START = 2;
	// outer is -x
	// inner is +y
	private final int SOUTH_EAST_START = 3;

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
	private static boolean northInitComplete = false;
	private static boolean southInitComplete = false;
	private static int northTravelDist = 0;
	private static int southTravelDist = 0;
	
	/* The initial sequence involves moving toward home base in order to 
	 * determine the width of the map. Once the map width is known, 
	 * the matrices are instantiated.
	 */
	
	public wjc140030Agent(){
	ID = nextID++;
	if(agent1==null){
		agent1 = this;
	}else{
		agent2 = this;
	}
	if(debug) System.out.println("wjc140030Agent "+ID+" created!");
	}

	public class LocalMap {
	// x/y role of each arraylist changes based on the starting
	// corner
	// The coordinates (0,0) indicate the starting position of the
	// agent regardless of orientation. 
	public int maxY = 3;
	public int maxX = 3;
	public ArrayList<ArrayList<Integer>> grid;

	public LocalMap(){
		grid = new ArrayList<>(maxX);
		for(int x = 0; x<maxX; x++){
		ArrayList<Integer> column = new ArrayList<>(maxY);
		for(int y = 0; y<maxY; y++){
			column.add(UNEXPLORED);
		}
		grid.add(column);
		}
	}

	public Pos getGlobalPos(int x, int y){
		switch(START_CORNER){
		case NORTH_WEST_START:
		return new Pos(x,mapWidth-y);
		case NORTH_EAST_START:
		return new Pos(mapWidth-x, mapWidth-y);
		case SOUTH_WEST_START:
		return new Pos(x, y);
		case SOUTH_EAST_START:
		return new Pos(mapWidth-x, y);
		default:
		System.out.println("ERROR: invalid starting corner");
		return null;
		}
	}

	public int testGlobalObstacle(int x, int y){
		Pos p = getGlobalPos(x,y);
		return this.get(p.x, p.y);
	}
		
	public int get(int x, int y){
		if(x<0 || y<0){
		return BLOCKED;
		}else if(x>=maxX || y>=maxY){
		return UNEXPLORED;
		}else{
		return grid.get(x).get(y);
		}
	}

	public int getRelative(Pos origin, int deltaX, int deltaY){
		switch(START_CORNER){
		case NORTH_WEST_START:
		return this.get(origin.x+deltaX, origin.y-deltaY);
		case NORTH_EAST_START:
		return this.get(origin.x-deltaX, origin.y-deltaY);
		case SOUTH_WEST_START:
		return this.get(origin.x+deltaX, origin.y+deltaY);
		case SOUTH_EAST_START:
		return this.get(origin.x-deltaX, origin.y+deltaY);
		default:
		System.out.println("ERROR: invalid starting corner");
		return -1;
		}
	}

	private void increaseX(){
		maxX++;
		ArrayList<Integer> newColumn = new ArrayList<>(maxY);
		for(int y = 0; y<maxY; y++){
		newColumn.set(y, UNEXPLORED);
		}
		grid.add(newColumn);
	}

	private void increaseY(){
		// increase the size of each y array by one
		maxY++;
		for(int x = 0; x<maxX; x++){
		grid.get(x).add(UNEXPLORED);
		}
	}
	
	public void set(Pos p, int status){
		int x = p.x;
		int y = p.y;

		if(x<0){
		return;
		}else if(grid.size()>x){
		ArrayList<Integer> column = grid.get(x);
		if(y<0){
			return;
		}else if(column.size()>y){
			column.set(y, status);
		}else{
			increaseY();
			this.set(p,status);
		}
		}else{
		increaseX();
		this.set(p, status);
		}
	}

	public void print(){
		int yWidth = 0;
		int xWidth = grid.size();
		for(ArrayList<Integer> column : grid){
		if( column.size() > yWidth)
			yWidth = column.size();
		}

		
		System.out.print("[]");
		for (int col = 0; col<=xWidth; col++) {
		System.out.print("[]");
		}
		System.out.println();
		// print each row of the map
		for (int row = yWidth-1; row>=0; row--){
		System.out.print("[]");
		for(int column = 0; column<xWidth; column++){
			if(obstacleMap[column][row]==BLOCKED){
			System.out.print("[]");
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
		for (int col = 0; col<=xWidth; col++) {
		System.out.print("[]");
		}
		System.out.println();
	}
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
		int value  = fCost()-node.fCost();
		if(value==0){
		if(move==AgentAction.MOVE_WEST || move==AgentAction.MOVE_EAST){
			return -1;
		}else if(node.getMove()==AgentAction.MOVE_WEST || node.getMove()==AgentAction.MOVE_EAST){
			return 1;
		}else{
			return 0;
		}
		}else{
		return value;
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
	wjc140030Agent otherAgent = (wjc140030Agent)other;
	return otherAgent.ID==ID;
	}

	public wjc140030Agent getTeammate(){
	if(this.equals(agent1)){
		return agent2;
	}else if (this.equals(agent2)){
		return agent1;
	}else{
		System.out.println("ERROR: teammate not found");
		return null;
	}
	}
	
	private Pos currentPos(){
	if(mapWidth==-1){
		return localPos;
	}else{
		return globalPos;	
	}
	}
	
	
		
	/** Checks whether or not the given Pos is a chokepoint 1 square wide
	 *  Returns false if the square is an obstacle or any immediately adjacent
	 *  (north, south, east, west) square is unknown.
	 */
	private boolean isW1Chokepoint(Pos testPos){
	// check if the test position is a wall	
	if(testObstacle(testPos)){
		return false;
	}
	int testX = testPos.x;
	int testY = testPos.y;
	// indicates the orientation of the "hallway"
	boolean verticalChoke = false; // open spaces north and south
	boolean horizontalChoke = false; // open spaces east and west
	boolean topRightChoke = false; // top left and bottom right blocked
	boolean topLeftChoke = false; // top right and bottom left blocked
	/*
	 * Check for vertical corridor, i.e. walls on the east and west 
	 * but not directly north or directly south
	 */
	// no obstacles directly north or directly south
	if( !(testObstacle(new Pos(testX, testY+1)) || testObstacle(new Pos(testX, testY+1)) ) ){
		// test if the west cell is blocked
		if(testObstacle(new Pos(testX-1, testY)) && 
		   // test if the northEast, east, or southeast cells are blocked
		   (testObstacle(new Pos(testX+1, testY+1))  || 
		testObstacle(new Pos(testX+1, testY)) ||
		testObstacle(new Pos(testX+1, testY-1)))){
		verticalChoke = true;
		}else if(testObstacle(new Pos(testX+1, testY)) && // test if the east cell is blocked
			 // test if the northWest, west, or southWest cells are blocked
			 (testObstacle(new Pos(testX-1, testY+1))|| 
			  testObstacle(new Pos(testX-1, testY)) ||
			  testObstacle(new Pos(testX-1, testY-1)))){
		verticalChoke = true;
		}
	}
	/*
	 * Check for horizontal corridor, i.e. walls on the north and south 
	 * but not directly east or directly west
	 */
	if( !(testObstacle(new Pos(testX-1, testY)) || testObstacle(new Pos(testX+1, testY)) ) ){
		// test if the north cell is blocked
		if(testObstacle(new Pos(testX, testY+1)) && 
		   // test if the southEast, south, or southWest cells are blocked
		   (testObstacle(new Pos(testX-1, testY-11)) || 
		testObstacle(new Pos(testX,   testY-1)) ||
		testObstacle(new Pos(testX+1, testY-1)))){
		horizontalChoke = true;
		}else if(testObstacle(new Pos(testX, testY-1)) && // test if the south cell is blocked
			 // test if the northEast, north, or northWest cells are blocked
			 (testObstacle(new Pos(testX-1, testY+1)) || 
			  testObstacle(new Pos(testX,   testY+1)) ||
			  testObstacle(new Pos(testX+1, testY+1)))){
		horizontalChoke = true;
		}
	}
	/*
	 * Check for topRightChoke
	 * topLeft cell blocked and bottomRight cell blocked
	 */
	if( testObstacle(new Pos(testX-1,testY+1)) && testObstacle(new Pos(testX+1,testY-1))  ){
		topRightChoke = true;
	}
	/*
	 * Check for topLeftChoke
	 * topRight cell blocked and bottomRight cell blocked
	 */
	if( testObstacle(new Pos(testX+1,testY+1)) && testObstacle(new Pos(testX-1,testY-1))  ){
		topLeftChoke = true;
	}

	return topRightChoke || topLeftChoke || verticalChoke || horizontalChoke;
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
	prevPos = currentPos().clone();
	
	switch(m){
	case AgentAction.MOVE_EAST:
		if(leftStart()){
		currentPos().x++;
		}else{
		currentPos().x--;
		}
		break;
	case AgentAction.MOVE_NORTH:
		if(START_CORNER == NORTH_WEST_START || START_CORNER == NORTH_EAST_START )
		currentPos().y--;
		else
		currentPos().y++;
		break;
	case AgentAction.MOVE_WEST:
		if(rightStart())
		currentPos().x--;
		else
		currentPos().x++;
		break;
	case AgentAction.MOVE_SOUTH:
		if(START_CORNER == NORTH_WEST_START || START_CORNER == NORTH_EAST_START )
		currentPos().y--;
		else
		currentPos().y++;
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
	private PathSearchNode getPath(Pos start, Pos goal, boolean hasFlag){
	// clear the debugging grid
	if(debug) debugPathGrid = new boolean[obstacleMap.length][obstacleMap.length];
	if(goal==null || goal.equals(start))
		return new PathSearchNode(AgentAction.DO_NOTHING, 0, 0, start, null);
	//A* search to goal
	PathSearchNode currentNode = new PathSearchNode(AgentAction.DO_NOTHING, 0,
							manhattanDist(start, goal), start, null);
	PriorityQueue<PathSearchNode> heap = new PriorityQueue<>();
	HashMap<Pos,PathSearchNode> searchHistory = new HashMap<>();
	heap.add(currentNode);
			
	int loopCount = 0;
	int loopLimit = obstacleMap.length*obstacleMap.length;
	while(!heap.isEmpty() && loopCount < 999){
		loopCount++;
		
		// select a node to expand
		currentNode = heap.poll();
		if(searchHistory.containsKey(currentNode.getPos())){
		continue;
		}
		searchHistory.put(currentNode.getPos(), currentNode); // add it to the search history
		
		//if(debug) debugPathGrid[currentNode.getPos().x][currentNode.getPos().y]=true;
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
			!testTeammate(temp) /*&& testEnemy(temp)!=-1.0 */ && !testObstacle(temp)) {
			PathSearchNode newNode = new PathSearchNode(directions[i], currentNode.pathCost+1,
									manhattanDist(temp,goal), temp, currentNode);
			heap.add(newNode);
			}
		}
		}
	}
	if(debug) System.out.println("\nERROR: Search Failed");
	return new PathSearchNode(AgentAction.DO_NOTHING, 0, 0, start, null);
	}
	
	private int moveTowards(Pos goal, boolean hasFlag){
	PathSearchNode currentNode = getPath(currentPos(), goal, hasFlag);
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
	if(mapWidth==-1){
		localMap.set(p, status);
	}else{
		try{
		obstacleMap[p.x][p.y] = status;
		}catch(IndexOutOfBoundsException e){}
	}
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
		return 1.0==agentMap[p.x][p.y];
	}catch(IndexOutOfBoundsException e){
		return true;
	}
	}

	private double testEnemy(Pos p){
	try{
		return agentMap[p.x][p.y];
	}catch(IndexOutOfBoundsException e){
		return 0.0;
	}
	}
	
	private boolean testObstacle(Pos p){
	try{
		return obstacleMap[p.x][p.y]==BLOCKED;
	}catch(IndexOutOfBoundsException e){
		return true;
	}
	}

	private boolean leftStart(){
	return START_CORNER==NORTH_WEST_START || START_CORNER==SOUTH_WEST_START;
	}

	private boolean rightStart(){
		return START_CORNER==NORTH_EAST_START || START_CORNER==SOUTH_EAST_START;
	}
	
	private void updateMaps(AgentEnvironment e){

	int newAgentProbMapSize = ((moveHistory.size()+1)*(moveHistory.size()+2))/2;
	ListIterator arrItr = agentProbMap.listIterator();
	while(arrItr.hasNext()){
		arrItr.next().add(new Double(1/newAgentProbMapSize));
		ListIterator insideItr = arrItr.next().listIterator();
		while(insideItr.hasNext()){
			insideItr.next() = new Double(1/newAgentProbMapSize);
		}			
	}	

	// execute if we don't know the width of the map
	if(mapWidth==-1){
		// test for our teammates position
		if(!e.isAgentNorth(e.OUR_TEAM, false) && !e.isAgentSouth(e.OUR_TEAM, false)){
		// compute the map width
		mapWidth = getTeammate().localMap.maxY;
		}else if(e.isAgentNorth(e.OUR_TEAM, true)){
		
		}else if(e.isAgentSouth(e.OUR_TEAM, true)){
		
		}else if(e.isBaseNorth(e.ENEMY_TEAM, true)){
		
		}else if(e.isBaseSouth(e.ENEMY_TEAM, true)){
				
		}
	}

	boolean right = e.isObstacleEastImmediate();
	boolean up = e.isObstacleNorthImmediate();
	boolean left = e.isObstacleWestImmediate();
	boolean down = e.isObstacleSouthImmediate();
	if(debug && currentPos()==null) System.out.println("ERROR: currentPos==null");

	int x = currentPos().x;
	int y = currentPos().y;

	insertObstacle(new Pos(x+1, y),   right ? BLOCKED : EMPTY);
	insertObstacle(new Pos(x,   y+1), up	? BLOCKED : EMPTY);
	insertObstacle(new Pos(x-1, y),   left  ? BLOCKED : EMPTY);
	insertObstacle(new Pos(x,   y-1), down  ? BLOCKED : EMPTY);

	// Update immediate agent positions
	boolean agentRightImm = e.isAgentEast(e.ENEMY_TEAM, true);
	boolean agentUpImm = e.isAgentNorth(e.ENEMY_TEAM, true);
	boolean agentLeftImm = e.isAgentWest(e.ENEMY_TEAM, true);
	boolean agentDownImm = e.isAgentSouth(e.ENEMY_TEAM, true);

	//list of voolean variables which indicate an enemy is mor ethan one
	//square away from the agent in a given direction.
	boolean agentRightFar = e.isAgentEast(e.ENEMY_TEAM, true);
	boolean agentUpFar = e.isAgentNorth(e.ENEMY_TEAM, true);
	boolean agentLeftFar = e.isAgentWest(e.ENEMY_TEAM, true);
	boolean agentDownFar = e.isAgentSouth(e.ENEMY_TEAM, true);

	/*
	value of agent[x][y] = probability of enemy being in a square.

	checks to see if the enemy is far away (mor than 1 space) and updates 
	agentMap accordingly. Very basic right now. Does not take into account
	how many spaces the other team has moved. Just stores where they could be
	based on the isAgent[Direction] function
	*/

	for(int i = 0; i < agentMap.length;i++)
		for(int j = 0 ; j < agentMap[i].length;j++)
			insertAgent(new Pos(i,j),1);

	if(agentRightFar){
		for(int i = x+1; i < agentMap.length;i++)
			for(int j = 0 ; j < agentMap[i].length;j++)
				insertAgent(new Pos(i,j),0);
	}	  	
	if(agentUpFar){
		for(int i = 0; i < agentMap.length;i++)
			for(int j = y+1 ; j < agentMap[i].length;j++)
				insertAgent(new Pos(i,j),0);
	}
	if(agentLeftFar){
		for(int i = x-1; i >=0 ;i--)
			for(int j = 0 ; j < agentMap[i].length;j++)
				insertAgent(new Pos(i,j),0);
	}
	if(agentDownFar){
		for(int i = 0; i < agentMap.length;i++)
			for(int j = y-1 ; j >=0 ;j--)
				insertAgent(new Pos(i,j),0);
	}

	boolean[] agents 
		= {agentRightImm, agentUpImm, agentLeftImm, agentDownImm};
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
		if(agentRightImm)
		insertAgent(new Pos(x+1,y), -1);
		if(agentLeftImm)
		insertAgent(new Pos(x-1,y), -1);
		if(agentUpImm)
		insertAgent(new Pos(x,y+1), -1);
		if(agentDownImm)
		insertAgent(new Pos(x,y-1), -1);
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
		}else if(column==currentPos().x && row == currentPos().y){
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
	System.out.print("[ ]");
	for (int col = 0; col<=width; col++) {
		System.out.print("[ ]");
	}
	System.out.println();
	// print each row of the map
	for (int row = width-1; row>=0; row--){
		System.out.print("[ ]");
		for(int column = 0; column<width; column++){
		double prob = agentMap[column][row];
		if(obstacleMap[column][row]==BLOCKED){
			System.out.print("[ ]");
		} else if(prob==1.0){
			System.out.print("1.0");
		} else if(prob==0.0){
			System.out.print("   ");
		} else{
			System.out.printf("%.2f", prob);
		}			
		
		}
		System.out.println("[ ]");
	}
	//print the bottom edge of the map
	System.out.print("[ ]");
	for (int col = 0; col<=width; col++) {
		System.out.print("[ ]");
	}
	System.out.println();
	}

	private void printChokeMap(){
	int width = chokepointMap.length;
	// print the top of the map
	System.out.print(" ");
	for (int col = 0; col<width; col++) {
		System.out.print("---");
	}
	System.out.println();
	// print each row of the map
	for (int row = width-1; row>=0; row--){
		System.out.print("|");
		for(int column = 0; column<width; column++){
		switch(chokepointMap[column][row]) {
		case TOPLEFT:
			System.out.print(" \\ ");
			break;
		case TOPRIGHT:
			System.out.print(" / ");
			break;
		case VERTICAL:
			System.out.print(" | ");
			break;
		case HORIZONTAL:
			System.out.print(" - ");
			break;
		case CROSS:
			System.out.print(" + ");
			break;
		case DIAG:
			System.out.print(" x ");
			break;
		default:
			if(obstacleMap[column][row]==BLOCKED){
			System.out.print("[ ]");
			}else{
			System.out.print("   ");
			}
		}
		}
		System.out.println("|");
	}
	//print the bottom edge of the map
	System.out.print(" ");
	for (int col = 0; col<width; col++) {
		System.out.print("---");
	}
	System.out.println();
	}


	private void printChokeWeights(){
	int width = chokepointMap.length;
	// print the top of the map
	System.out.print(" ");
	for (int col = 0; col<width; col++) {
		System.out.print("---");
	}
	System.out.println();
	// print each row of the map
	for (int row = width-1; row>=0; row--){
		System.out.print("|");
		for(int column = 0; column<width; column++){
		if(obstacleMap[column][row]==BLOCKED){
			System.out.print("[ ]");
		}else{
			System.out.printf("% 3d",chokeWeightMap[column][row]);
		}
		}
		System.out.println("|");
	}
	//print the bottom edge of the map
	System.out.print(" ");
	for (int col = 0; col<width; col++) {
		System.out.print("---");
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
	private void createMaps(){
	obstacleMap = new int[mapWidth][mapWidth];
	LocalMap otherLocalMap = getTeammate().localMap;
	for(int i = 0; i< mapWidth; i++){
		for (int j = 0; j<mapWidth; j++) {
		if(localMap.testGlobalObstacle(i, j) != UNEXPLORED){
			obstacleMap[i][j] = localMap.testGlobalObstacle(i,j);
		}else if(otherLocalMap.testGlobalObstacle(i,j) != UNEXPLORED){
			obstacleMap[i][j] = otherLocalMap.testGlobalObstacle(i,j);
		}else{
			obstacleMap[i][j] = UNEXPLORED;
		}
		}
	}
	if(debug) System.out.println("Maps Merged!");
	for(int i = 0; i< mapWidth; i++){
		obstacleMap[0][i] = EMPTY;
		obstacleMap[mapWidth-1][i] = EMPTY;
	}
	agentMap = new double[mapWidth][mapWidth];
	chokepointMap = new int[mapWidth][mapWidth];
	chokeWeightMap = new int[mapWidth][mapWidth];
	for(int x = 0; x<mapWidth; x++){
		for(int y = 0; y<mapWidth; y++){
		chokepointMap[x][y] = NOT_CHOKEPOINT;
		}		
	}

	this.setGlobalPos(localMap);
	getTeammate().setGlobalPos(otherLocalMap);
	
	int baseYcoord = mapWidth/2;
	if(leftStart()){
		homeBase = new Pos(0, baseYcoord);
		enemyBase = new Pos(mapWidth-1, baseYcoord);
	}else if(rightStart()){
		homeBase = new Pos(mapWidth-1, baseYcoord);
		enemyBase = new Pos(0, baseYcoord);
	}else{
		System.out.println("ERROR: invalid start side");
	}
	}

	private void insertMapData(){
	// localMap
	// bomb map
	// agent map
	// chokemap
	}
	
	private void setGlobalPos(LocalMap map){
	//globalPos = LocalMap.getGlobalPos(loca);
	}

	/** 
	 * Calculates 6 paths between points of interest in the map and weights
	 * each chokepoint based on the number of optimal paths that use it
	 */
	private void weightChokepoints(){
	// clear the previous values
	for(int x = 0; x<chokeWeightMap.length; x++){
		for(int y = 0; y<chokeWeightMap[x].length; y++){
		chokeWeightMap[x][y] = 0;
		}
	}
	int width = obstacleMap.length;
	Pos rightBase = new Pos(width-1, width/2);
	Pos leftBase = new Pos(0, width/2);
	// calculate the paths that go from left to right
	PathSearchNode paths[] =
		{getPath(new Pos(0, width-1), rightBase, true),
		 getPath(leftBase, rightBase, true),
		 getPath(new Pos(0, 0), rightBase, true),
		 getPath(leftBase, new Pos(width-1, width-1), true),
		 getPath(leftBase, rightBase, true),
		 getPath(leftBase, new Pos(width-1, 0), true)};
	for(PathSearchNode destinationNode : paths){
		// unravel each path
		PathSearchNode currentNode = destinationNode;
		while(currentNode.getParent()!=null){
		currentNode = currentNode.getParent();
		// mark it in the chokepoint map
		Pos p = currentNode.getPos();
		if(p.equals(homeBase)) continue;
		if(destinationNode.equals(homeBase))
			chokeWeightMap[p.x][p.y]+=2;
		else
			chokeWeightMap[p.x][p.y]++;
		}
	}
	}

	/**
	 * PRECONDITION: on corner of home column
	 */
	public void setStartCorner(AgentEnvironment e){
	if(e.isObstacleNorthImmediate()){
		if(e.isObstacleWestImmediate()){
		START_CORNER = NORTH_WEST_START;
		}else if(e.isObstacleEastImmediate()){
		START_CORNER = NORTH_EAST_START;
		}else{
		if(debug)
			System.out.println("ERROR: updating start corner while not on home corner");
		}
	}else if(e.isObstacleSouthImmediate()){
		if(e.isObstacleWestImmediate()){
		START_CORNER = SOUTH_WEST_START;
		}else if(e.isObstacleEastImmediate()){
		START_CORNER = SOUTH_EAST_START;
		}else{
		if(debug)
			System.out.println("ERROR: updating start corner while not on home corner");
		}
	}else{
		if(debug)
		System.out.println("ERROR: updating start corner while not on home corner");
	}
	}
	
	public void setMode(AgentEnvironment e){
	if(e.hasFlag()){
		mode = ATTACK;
	}else if(e.hasFlag(e.OUR_TEAM)){
		// mode = FOLLOW
		mode = DEFEND;
	}else if(e.hasFlag(e.ENEMY_TEAM)){
		// mode = HUNT;
		mode = DEFEND;
	} else{
		wjc140030Agent teammate = (this.equals(agent1)) ? agent2 : agent1;
		if(teammate.currentPos()==null){
		agent1.mode = ATTACK;
		agent2.mode = DEFEND;
		return;
		}
		int myDist = this.pathLength(this.getPath(currentPos(), enemyBase, false));
		int otherDist = teammate.pathLength( teammate.getPath(teammate.currentPos(),enemyBase, false));
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
	if(hasFlag){
		return moveTowards(homeBase,true);	
	}else{
		return moveTowards(enemyBase,false);			
		// if(isW1Chokepoint(currentPos()) && (Math.random() > 0.925)){
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
	private Pos findBestChoke(){
	int minDist = chokeWeightMap.length; //breaks ties between chokepoints with equal weights
	Pos bestChoke = new Pos(0,0);

	for (int y = 0; y<chokeWeightMap.length; y++) {
		for(int x = 0; x<chokeWeightMap[y].length; x++){
		if(chokeWeightMap[x][y] > chokeWeightMap[bestChoke.x][bestChoke.y]){
			Pos p = new Pos(x,y);
			if(!p.equals(homeBase))
			bestChoke = p;
		}else if(chokeWeightMap[x][y]==chokeWeightMap[bestChoke.x][bestChoke.y]){
			// break ties by choosing the one closer to home base
			int d1 = pathLength(getPath(bestChoke,homeBase,true));
			Pos p = new Pos(x,y);
			if(p.equals(homeBase))
			continue;
			int d2 = pathLength(getPath(p,homeBase,true));
			bestChoke = (d2<d1) ? new Pos(x,y) : bestChoke;
		}
		}
	}
	return bestChoke;

	}

	
	public int defenseModeMove(){
	int goalX = obstacleMap.length/2;
	int goalY = 3*obstacleMap.length/4;
	Pos checkPoint1 = new Pos(goalX, goalY);
	if(doneExploring){
		if(debug) System.out.println("Done Exploring.");
		weightChokepoints();
		if(debug )printChokeMap();
		// find a chokepoint to guard
		Pos bestChoke = findBestChoke();		
		// move towards that chokepoint
		return moveTowards(bestChoke, false);// if we're in defense mode then we don't have the flag
	}else{
		// explore the region around home base
		if(debug) System.out.println("Eploring region.");
		for(int y = goalY; y>obstacleMap.length/4; y--){
		if(obstacleMap[goalX][y]==UNEXPLORED &&
		   !(currentPos().x == goalX && currentPos().y == goalY )){
			return moveTowards(new Pos(goalX, y), false);
		}
		}
		doneExploring = true;
		return defenseModeMove();
	}	
	}

	public int getMove( AgentEnvironment env ) {
	if (debug) System.out.println("***** Processing Agent "+ID+" *****");

	updateMaps(env);
	
	/** Check if the agent has been tagged or exploded */
	if((env.isObstacleNorthImmediate() || env.isObstacleSouthImmediate()) && onHomeCol(env)){
		prevPos = currentPos().clone();
		agentMap[prevPos.x][prevPos.y] = 0;
		// TODO: Test whether somebody scored
		if(leftStart()){
		currentPos().x = 0;
		if(env.isBaseSouth(AgentEnvironment.OUR_TEAM, false)){
			currentPos().y = obstacleMap.length-1;
		}else{
			currentPos().y = 0;
		}
		}else{
		currentPos().x = obstacleMap.length-1;
		if(env.isBaseSouth(AgentEnvironment.OUR_TEAM, false)){
			currentPos().y = obstacleMap.length-1;
		}else{
			currentPos().y = 0;
		}
		}
	}
	
	/** Separate responsibilities*/
	int finalMove = 0;
	//setMode(env);
	mode = ATTACK;
	switch(mode){
	case ATTACK:
		if(debug) System.out.println("ATTACK MODE");
		finalMove = attackModeMove(env.hasFlag());
		break;
	case DEFEND:
		if(debug) System.out.println("DEFEND MODE");
		finalMove = defenseModeMove();
		break;
	case HUNT:
		break;
	case FOLLOW:
		break;
	default:
	}	

	if(debug) {
		if(mapWidth==-1){
		localMap.print();
		}else{
		printObstacleMap();		
		}
		//printChokeMap();
		//printAgentMap();
	}

	
	return recordMove(finalMove);
	}

	public void drawIcon(Graphics g, int width, int height){
	// draw a circle lol
	int xCenter = width/2;
	int yCenter = height/2;

	// Draw pac-mans body
	g.setColor(Color.yellow);
	g.fillArc(0,0,width-1,height-1, -135, 270);
	// draw angry eye
	g.setColor(Color.black);
	int y = (int)Math.round(width/6.0);
	int xEyeBrow[] = {};
	int yEyeBrow[] = {};
	g.fillOval(xCenter, y, y, y); 
	// Draw a black outline
	g.setColor(Color.black);
	g.drawArc(0, 0, width-1, height-1, -135, 270);
	// double radius = width/2;
	// int x = (int) Math.round(radius*Math.sqrt(.5));
	// System.out.println(x);
	// g.drawLine(xCenter, yCenter, -x, x);
	// g.drawLine(xCenter,yCenter,-x,-x);
	}
}
