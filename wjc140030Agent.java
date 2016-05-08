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
    private static CompleteGrid completeMap;
    private DynamicGrid dynamicMap = new DynamicGrid();
    private Grid currentMap = dynamicMap;
    private static final int BLOCKED = 100;
    private static final int EMPTY = 0;
    private static final int UNEXPLORED = 102;
    private int[][] localDebugGrid = null;
    private int[][]  completeDebugGrid = null;

    /** 
     * A map of key strategic chokepoints 
     */    
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
    private boolean touchedHomeBase = false;
    private boolean touchedEnemyBase = false;
    private Pos currentPos = new Pos(0, 0);
    // the coordinates of the agent last turn
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
    private static wjc140030Agent agent1;
    private static wjc140030Agent agent2;
    
    /**
     * Share the intentions with teammates
     */
    private PathSearchNode intention; // contains the path that the agent will follow
    private final int DEFEND = 0;
    private final int ATTACK = 1;
    private final int HUNT   = 2;
    private final int FOLLOW = 3;
    private int mode;
    private boolean doneExploring = false;
    
    /** 
     * Boolean used to determine the starting position 
     */
    private int startCorner;
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

    private abstract class Grid{
	// the width of the map contained in a subclass to prevent accidental
	// overwrites
	protected int width;
	public int width(){return width;}
	private HashMap<Pos, Boolean> bombMap = new HashMap<>();
	
	public abstract void update(AgentEnvironment e);
	public abstract void insertObstacle(Pos p, int status, boolean relative); // sets the value at p to status
	public abstract void insertBomb(Pos p, boolean relative); // inserts a bomb into the hashmap of positions
	public abstract void removeBomb(Pos p, boolean relative); // remvoves a bomb from the hashmap
	public abstract void insertAgent(Pos p, double prob, boolean relative); //
	public abstract void clearChokeWeights();
	public abstract void incChokeWeight(Pos p, double weight, boolean relative); //
	public abstract void setChokeWeight(Pos p, double weight, boolean relative); // 
	
	public abstract Pos toAbsPos(Pos p);
	
	public abstract int testObstacle(Pos p, boolean relative);
	public abstract boolean testBomb(Pos p, boolean relative);
	public abstract boolean isBlocked(Pos p, boolean relative);
	public abstract double testEnemy(Pos p, boolean relative);
	public abstract boolean testTeammate(Pos p, boolean relative);
	public abstract boolean isChoke(Pos p, boolean relative);
	public abstract double getChokeWeight(Pos p, boolean relative);
	public abstract Pos findBestChoke();
	
	public abstract void printObstacleMap();
	public abstract void printChokeMap();
	public abstract void printChokeWeights();
	public abstract void printAgentMap();
    }
    
    private class CompleteGrid extends Grid{
	// Whether not each cell is empty, blocked, or unexplored.
	private int[][] obstacleMap;
	// each cell contains the probability that an enemy agent is there.
	private double[][] agentMap;
	// Each cell contains the type of chokepoint, if any, at the given coordinates
	private int[][] chokeMap;
	// Each cell contains the importance of the chokepoint, if any (higher is more important)
	private double[][] chokeWeightMap;

	public CompleteGrid(int width){
	    this.width = width;
	    obstacleMap = new int[width][width];
	    agentMap = new double[width][width];
	    chokeMap = new int[width][width];
	    chokeWeightMap = new double[width][width];
	}	

	public Pos toAbsPos(Pos p){
	    return new Pos(currentPos.x+p.x, currentPos.y+p.y);
	}
	
	public void insertAgent(Pos p, double status, boolean relative){
	    try{
		Pos absPos = relative ? toAbsPos(p) : p;
		agentMap[absPos.x][absPos.y] = status;
	    }catch(IndexOutOfBoundsException e){}
	}

	public void insertObstacle(Pos p, int status, boolean relative){
	    
	}
	
	public void insertBomb(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    bombMap.put(p, true);
	}

	public void removeBomb(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    bombMap.remove(absPos);
	}
    
	public boolean testTeammate(Pos p, boolean relative){
	    try{
		Pos absPos = relative ? toAbsPos(p) : p;
		return 1.0==agentMap[absPos.x][absPos.y];
	    }catch(IndexOutOfBoundsException e){
		return true;
	    }
	}
	
	public double testEnemy(Pos p, boolean relative){
	    try{
		Pos absPos = relative ? toAbsPos(p) : p;
		return agentMap[absPos.x][absPos.y];
	    }catch(IndexOutOfBoundsException e){
		return 0.0;
	    }
	}
    
	public int testObstacle(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    try{
		return obstacleMap[absPos.x][absPos.y];
	    }catch(IndexOutOfBoundsException e){
		return BLOCKED;
	    }
	}

	public boolean isBlocked(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    try{
		return obstacleMap[absPos.x][absPos.y]==BLOCKED;
	    }catch(IndexOutOfBoundsException e){
		return true;
	    }
	}

	public boolean testBomb(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;	    
	    return bombMap.containsKey(absPos);
	}

	public void setChokeWeight(Pos p, double weight, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    try{
		chokeWeightMap[absPos.x][absPos.y] = weight;	
	    }catch(IndexOutOfBoundsException e){}
	}

	public void incChokeWeight(Pos p, double weight, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    try{
		chokeWeightMap[absPos.x][absPos.y] += weight;	
	    }catch(IndexOutOfBoundsException e){}
	}

	public void clearChokeWeights(){
	    for(int x = 0; x < chokeWeightMap.length; x++){
		
	    }

	}

	public double getChokeWeight(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    try{
		return chokeWeightMap[absPos.x][absPos.y];	
	    }catch(IndexOutOfBoundsException e){
		return 0;
	    }
	}

	public void update(AgentEnvironment e){
	    // execute if we don't know the width of the map
	    boolean right = e.isObstacleEastImmediate();
	    boolean up = e.isObstacleNorthImmediate();
	    boolean left = e.isObstacleWestImmediate();
	    boolean down = e.isObstacleSouthImmediate();
	    if(debug && currentPos==null) System.out.println("ERROR: currentPos==null");

	    int x = currentPos.x;
	    int y = currentPos.y;

	    currentMap.insertObstacle(new Pos(1,  0),  right ? BLOCKED : EMPTY, true);
	    currentMap.insertObstacle(new Pos(0,  1),  up    ? BLOCKED : EMPTY, true);
	    currentMap.insertObstacle(new Pos(-1, 0),  left  ? BLOCKED : EMPTY, true);
	    currentMap.insertObstacle(new Pos(0,  -1), down  ? BLOCKED : EMPTY, true);
		

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
		    insertAgent(new Pos(i,j),1, true);

	    if(agentRightFar){
		for(int i = x+1; i < agentMap.length;i++)
		    for(int j = 0 ; j < agentMap[i].length;j++)
			insertAgent(new Pos(i,j),0, true);
	    }	  	
	    if(agentUpFar){
		for(int i = 0; i < agentMap.length;i++)
		    for(int j = y+1 ; j < agentMap[i].length;j++)
			insertAgent(new Pos(i,j),0, true);
	    }
	    if(agentLeftFar){
		for(int i = x-1; i >=0 ;i--)
		    for(int j = 0 ; j < agentMap[i].length;j++)
			insertAgent(new Pos(i,j),0, true);
	    }
	    if(agentDownFar){
		for(int i = 0; i < agentMap.length;i++)
		    for(int j = y-1 ; j >=0 ;j--)
			insertAgent(new Pos(i,j),0, true);
	    }

	    boolean[] agents = {agentRightImm, agentUpImm, agentLeftImm, agentDownImm};
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
		    insertAgent(new Pos(x+1,y), -1, true);
		if(agentLeftImm)
		    insertAgent(new Pos(x-1,y), -1, true);
		if(agentUpImm)
		    insertAgent(new Pos(x,y+1), -1, true);
		if(agentDownImm)
		    insertAgent(new Pos(x,y-1), -1, true);
	    }
	}

	
	/** Checks whether or not the given Pos is a chokepoint 1 square wide
	 *  Returns false if the square is an obstacle or any immediately adjacent
	 *  (north, south, east, west) square is unknown.
	 */
	public boolean isChoke(Pos p, boolean relative){
	    Pos testPos = relative ? toAbsPos(p) : p;
	    // check if the test position is a wall	
	    if(currentMap.isBlocked(testPos, false)){
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
	    if( !(currentMap.isBlocked(new Pos(testX, testY+1), false) ||
		  currentMap.isBlocked(new Pos(testX, testY+1), false))){
		// test if the west cell is blocked
		if(currentMap.isBlocked(new Pos(testX-1, testY), false) && 
		   // test if the northEast, east, or southeast cells are blocked
		   (currentMap.isBlocked(new Pos(testX+1, testY+1), false)  || 
		    currentMap.isBlocked(new Pos(testX+1, testY), false) ||
		    currentMap.isBlocked(new Pos(testX+1, testY-1), false))){
		    verticalChoke = true;
		}else if(currentMap.isBlocked(new Pos(testX+1, testY), false) && // test if the east cell is blocked
			 // test if the northWest, west, or southWest cells are blocked
			 (currentMap.isBlocked(new Pos(testX-1, testY+1), false)|| 
			  currentMap.isBlocked(new Pos(testX-1, testY), false) ||
			  currentMap.isBlocked(new Pos(testX-1, testY-1), false))){
		    verticalChoke = true;
		}
	    }
	    /*
	     * Check for horizontal corridor, i.e. walls on the north and south 
	     * but not directly east or directly west
	     */
	    if( !(currentMap.isBlocked(new Pos(testX-1, testY), false) ||
		  currentMap.isBlocked(new Pos(testX+1, testY), false) ) ){
		// test if the north cell is blocked
		if(currentMap.isBlocked(new Pos(testX, testY+1), false) && 
		   // test if the southEast, south, or southWest cells are blocked
		   (currentMap.isBlocked(new Pos(testX-1, testY-1), false) || 
		    currentMap.isBlocked(new Pos(testX,   testY-1), false) ||
		    currentMap.isBlocked(new Pos(testX+1, testY-1), false))){
		    horizontalChoke = true;
		}else if(currentMap.isBlocked(new Pos(testX, testY-1), false) && // test if the south cell is blocked
			 // test if the northEast, north, or northWest cells are blocked
			 (currentMap.isBlocked(new Pos(testX-1, testY+1), false) || 
			  currentMap.isBlocked(new Pos(testX,   testY+1), false) ||
			  currentMap.isBlocked(new Pos(testX+1, testY+1), false))){
		    horizontalChoke = true;
		}
	    }
	    /*
	     * Check for topRightChoke
	     * topLeft cell blocked and bottomRight cell blocked
	     */
	    if( currentMap.isBlocked(new Pos(testX-1,testY+1), false) &&
		currentMap.isBlocked(new Pos(testX+1,testY-1), false)  ){
		topRightChoke = true;
	    }
	    /*
	     * Check for topLeftChoke
	     * topRight cell blocked and bottomRight cell blocked
	     */
	    if( currentMap.isBlocked(new Pos(testX+1,testY+1), false) &&
		currentMap.isBlocked(new Pos(testX-1,testY-1), false)  ){
		topLeftChoke = true;
	    }

	    return topRightChoke || topLeftChoke || verticalChoke || horizontalChoke;
	}
	
	/**
	 * Returns the best chokepoint to guard.
	 * PRECONDITION: the chokepoints have been weigthed
	 */
	public Pos findBestChoke(){
	    int minDist = chokeWeightMap.length; //breaks ties between chokepoints with equal weights
	    Pos bestChoke = new Pos(0,0);

	    for (int x = 0; x<chokeWeightMap.length; x++) {
		for(int y = 0; x<chokeWeightMap[x].length; y++){
		    
		}
	    }
	    return bestChoke;

	}

	
	public void printObstacleMap() {
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
			// }else if(completeDebugGrid!=null && completeDebugGrid[column][row]==){
			// System.out.print("--");
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

	public void printAgentMap() {
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
		    } else if(prob==0.0){
			System.out.print("   ");
		    } else{
			System.out.printf("%3.2f", prob);
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

	public void printChokeMap(){
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
		    switch(chokeMap[column][row]) {
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
			if(currentMap.isBlocked(new Pos(column, row), false)){
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


	public void printChokeWeights(){
	    int width = chokeMap.length;
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
		    Pos p = new Pos(column, row);
		    if(isBlocked(p, false)){
			System.out.print("[ ]");
		    }else{
			System.out.printf("% 3d",currentMap.getChokeWeight(p, false));
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

    }
    
    private class DynamicGrid extends Grid {
	// x/y role of each arraylist changes based on the starting
	// corner
	// The coordinates (0,0) indicate the starting position of the
	// agent regardless of orientation. 
	int width = 0;
	private ArrayList<ArrayList<Integer>> obstacleMap;
	private ArrayList<ArrayList<Double>> agentMap;
	private ArrayList<ArrayList<Double>> chokeWeightMap;
	
	
	public DynamicGrid(){
	    obstacleMap = new ArrayList<>(width);
	    for(int x = 0; x<width; x++){
		ArrayList<Integer> column = new ArrayList<>(width);
		for(int y = 0; y<width; y++){
		    column.add(UNEXPLORED);
		}
		obstacleMap.add(column);
	    }
	}

	public Pos getAbsPos(Pos p){
	    switch(startCorner){
	    case NORTH_WEST_START:
		return new Pos(p.x,-p.y);
	    case NORTH_EAST_START:
		return new Pos(-p.x,-p.y);
	    case SOUTH_WEST_START:
		return new Pos(p.x, p.y);
	    case SOUTH_EAST_START:
		return new Pos(-p.x, p.y);
	    default:
		System.out.println("ERROR: invalid starting corner");
		return null;
	    }
	}

	public void insertBomb(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;	    
	    bombMap.put(absPos, true);
	}

	public boolean testBomb(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    return bombMap.containsKey(absPos);
	}
	
	public void removeBomb(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    bombMap.remove(absPos);
	}

	public double getChokeWeight(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    try{
		return chokeWeightMap.get(absPos.x).get(absPos.y);
	    }catch(IndexOutOfBoundsException e){
		return 0;
	    }
	}

	/** Checks whether or not the given Pos is a chokepoint 1 square wide
	 *  Returns false if the square is an obstacle or any immediately adjacent
	 *  (north, south, east, west) square is unknown.
	 */
	public boolean isChoke(Pos p, boolean relative){
	    Pos testPos = relative ? toAbsPos(p) : p;
	    // check if the test position is a wall	
	    if(currentMap.isBlocked(testPos, false)){
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
	    if( !(currentMap.isBlocked(new Pos(testX, testY+1), false) ||
		  currentMap.isBlocked(new Pos(testX, testY+1), false))){
		// test if the west cell is blocked
		if(currentMap.isBlocked(new Pos(testX-1, testY), false) && 
		   // test if the northEast, east, or southeast cells are blocked
		   (currentMap.isBlocked(new Pos(testX+1, testY+1), false)  || 
		    currentMap.isBlocked(new Pos(testX+1, testY), false) ||
		    currentMap.isBlocked(new Pos(testX+1, testY-1), false))){
		    verticalChoke = true;
		}else if(currentMap.isBlocked(new Pos(testX+1, testY), false) && // test if the east cell is blocked
			 // test if the northWest, west, or southWest cells are blocked
			 (currentMap.isBlocked(new Pos(testX-1, testY+1), false)|| 
			  currentMap.isBlocked(new Pos(testX-1, testY), false) ||
			  currentMap.isBlocked(new Pos(testX-1, testY-1), false))){
		    verticalChoke = true;
		}
	    }
	    /*
	     * Check for horizontal corridor, i.e. walls on the north and south 
	     * but not directly east or directly west
	     */
	    if( !(currentMap.isBlocked(new Pos(testX-1, testY), false) ||
		  currentMap.isBlocked(new Pos(testX+1, testY), false) ) ){
		// test if the north cell is blocked
		if(currentMap.isBlocked(new Pos(testX, testY+1), false) && 
		   // test if the southEast, south, or southWest cells are blocked
		   (currentMap.isBlocked(new Pos(testX-1, testY-1), false) || 
		    currentMap.isBlocked(new Pos(testX,   testY-1), false) ||
		    currentMap.isBlocked(new Pos(testX+1, testY-1), false))){
		    horizontalChoke = true;
		}else if(currentMap.isBlocked(new Pos(testX, testY-1), false) && // test if the south cell is blocked
			 // test if the northEast, north, or northWest cells are blocked
			 (currentMap.isBlocked(new Pos(testX-1, testY+1), false) || 
			  currentMap.isBlocked(new Pos(testX,   testY+1), false) ||
			  currentMap.isBlocked(new Pos(testX+1, testY+1), false))){
		    horizontalChoke = true;
		}
	    }
	    /*
	     * Check for topRightChoke
	     * topLeft cell blocked and bottomRight cell blocked
	     */
	    if( currentMap.isBlocked(new Pos(testX-1,testY+1), false) &&
		currentMap.isBlocked(new Pos(testX+1,testY-1), false)  ){
		topRightChoke = true;
	    }
	    /*
	     * Check for topLeftChoke
	     * topRight cell blocked and bottomRight cell blocked
	     */
	    if( currentMap.isBlocked(new Pos(testX+1,testY+1), false) &&
		currentMap.isBlocked(new Pos(testX-1,testY-1), false)  ){
		topLeftChoke = true;
	    }

	    return topRightChoke || topLeftChoke || verticalChoke || horizontalChoke;
	}
	
	public boolean testTeammate(Pos p, boolean relative){
	    try{
		Pos absPos = relative ? toAbsPos(p) : p;
		return 1.0==agentMap.get(absPos.x).get(absPos.y);
	    }catch(IndexOutOfBoundsException e){
		return true;
	    }
	}

	public double testEnemy(Pos p, boolean relative){
	    try{
		Pos absPos = relative ? toAbsPos(p) : p;
		return agentMap.get(absPos.x).get(absPos.y);
	    }catch(IndexOutOfBoundsException e){
		return 0.0;
	    }
	}
    
	public boolean isBlocked(Pos p, boolean relative){
	    try{
		Pos absPos = relative ? toAbsPos(p) : p;
		return obstacleMap.get(absPos.x).get(absPos.y)==BLOCKED;
	    }catch(IndexOutOfBoundsException e){
		return true;
	    }
	}
			
	public int testGlobalObstacle(Pos p){
	    int width = completeMap.width()-1;
	    switch(startCorner){
	    case NORTH_WEST_START:
		return this.testObstacle(new Pos(p.x, width-p.y), false);
	    case NORTH_EAST_START:
		return this.testObstacle(new Pos(width-p.x, width-p.y), false);
	    case SOUTH_WEST_START:
		return this.testObstacle(new Pos(p.x, p.y), false);
	    case SOUTH_EAST_START:
		return this.testObstacle(new Pos(width-p.x, p.y), false);
	    default:
		System.out.println("ERROR: invalid starting corner");
		return -1;
	    }
	}

	private void increaseWidth(){
	    width++;
	    ArrayList<Integer> newColumn = new ArrayList<>(width);
	    for(int i = 0; i<width; i++){
		newColumn.add(UNEXPLORED);
		obstacleMap.get(i).add(UNEXPLORED);
	    }
	    obstacleMap.add(newColumn);
	}

	public Pos toAbsPos(Pos p){
	    int x = currentPos.x+p.x;
	    int y = currentPos.y+p.y;
	    switch(startCorner){
	    case NORTH_WEST_START:
		return new Pos(x,-y);
	    case NORTH_EAST_START:
		return new Pos(-x,-y);
	    case SOUTH_WEST_START:
		return new Pos(x,y);
	    case SOUTH_EAST_START:
		return new Pos(-x,y);
	    default:
		System.out.println("ERROR: invalid starting corner");
		return null;
	    }
	}
	
	public int testObstacle(Pos p, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    if(absPos.x<0 || absPos.y<0){
		return BLOCKED;
	    }else if(width>absPos.x && width>absPos.y){
		return obstacleMap.get(absPos.x).get(absPos.y);
	    }else{
		return UNEXPLORED;
	    }
	}

	
	public void insertObstacle(Pos p, int status, boolean relative){
	    Pos absPos = relative ? toAbsPos(p) : p;
	    int x = absPos.x;
	    int y = absPos.y;

	    if(x<0 || y<0){
		return;
	    }else if(width>x && width>y ){
		ArrayList<Integer> column = obstacleMap.get(x);
		column.set(y, status);
	    }else{
		increaseWidth();
		this.insertObstacle(absPos, status, false);
	    }
	}
	
	public void insertAgent(Pos p, double status, boolean relative){
	    Pos absPos =  relative ? toAbsPos(p) : p;
	    if(absPos.x < 0 || absPos.y < 0){
		return;
	    }else if(absPos.x < width && absPos.y < width){
		agentMap.get(absPos.x).set(absPos.y, status);
	    }else{
		increaseWidth();
		insertAgent(absPos, status, false);
	    }
	}

	public Pos findBestChoke(){
	    System.out.println("findBestChoke() not implemented!");
	    return null;
	}
	
	public void setChokeWeight(Pos p, double weight, boolean relative){
	    Pos absPos =  relative ? toAbsPos(p) : p;
	    chokeWeightMap.get(absPos.x).set(absPos.y, weight);
	}

	public void incChokeWeight(Pos p, double weight, boolean relative){
	    Pos absPos =  relative ? toAbsPos(p) : p;
	    double newVal = chokeWeightMap.get(absPos.x).get(absPos.y);
	    chokeWeightMap.get(absPos.x).set(absPos.y, newVal+weight);
	}

	public void clearChokeWeights(){
	    for(int x = 0; x<width; x++){
		for(int y = 0; y < width; y++){
		    chokeWeightMap.get(x).set(y,0.0);
		}
	    }
	}

	public void update(AgentEnvironment e){
        }
	
	public void printAgentMap(){
	    System.out.println("not implemented");
	}

	public void printChokeWeights() {
            
	}

	public void printChokeMap(){
	    
	}
	
	public void printObstacleMap(){
	    System.out.print("[]");
	    for (int col = 0; col<=width; col++) {
		System.out.print("[]");
	    }
	    System.out.println();
	    // print each row of the map
	    for (int row = width-1; row>=0; row--){
		System.out.print("[]");
		for(int column = 0; column<width; column++){
		    if(testObstacle(new Pos(column, row), false)==BLOCKED){
			System.out.print("[]");
		    }else if(testObstacle(new Pos(column, row), false)==UNEXPLORED){
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
    }

    public void merge(){
	int width = completeMap.width()-1;
	// copy the obstacle map
	for(int x = 0; x<width; x++){
	    for(int y = 0; y < width; y++){
		int status = dynamicMap.testGlobalObstacle(new Pos(x,y));
		if(status != UNEXPLORED){
		    switch(startCorner){
		    case NORTH_WEST_START:
			completeMap.insertObstacle(new Pos(x,completeMap.width()-y), status, false);
			break;
		    case NORTH_EAST_START:
			completeMap.insertObstacle(new Pos(completeMap.width()-x, completeMap.width()-y),
						   status, false);
			break;
		    case SOUTH_WEST_START:
			completeMap.insertObstacle(new Pos(x, y), status, false);
			break;
		    case SOUTH_EAST_START:
			completeMap.insertObstacle(new Pos(completeMap.width()-x, y), status, false);
			break;
		    default:
			System.out.println("ERROR: invalid starting corner");
			return;
		    }
		}
	    }
	}
	// copy the mine map
	// for(Pos p : )
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
	prevPos = currentPos.clone();
	
	switch(m){
	case AgentAction.MOVE_EAST:
	    if(leftStart()){
		currentPos.x++;
	    }else{
		currentPos.x--;
	    }
	    break;
	case AgentAction.MOVE_NORTH:
	    if(startCorner == NORTH_WEST_START || startCorner == NORTH_EAST_START )
		currentPos.y--;
	    else
		currentPos.y++;
	    break;
	case AgentAction.MOVE_WEST:
	    if(rightStart())
		currentPos.x--;
	    else
		currentPos.x++;
	    break;
	case AgentAction.MOVE_SOUTH:
	    if(startCorner == NORTH_WEST_START || startCorner == NORTH_EAST_START )
		currentPos.y--;
	    else
		currentPos.y++;
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
    private PathSearchNode getPath(Pos start, Pos goal, boolean hasFlag, boolean random){
	// clear the debugging grid
	// if(debug) debugPathGrid = new boolean[obstacleMap.length][obstacleMap.length];
	if(goal==null || goal.equals(start))
	    return new PathSearchNode(AgentAction.DO_NOTHING, 0, 0, start, null);
	//A* search to goal
	PathSearchNode currentNode = new PathSearchNode(AgentAction.DO_NOTHING, 0,
							manhattanDist(start, goal), start, null);
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
		
	    //if(debug) debugPathGrid[currentNode.getPos().x][currentNode.getPos().y]=true;
	    // goal test 
	    if(currentNode.getPos().equals(goal)){
		//unravel the stack
		return currentNode;
		
	    }else{
		// expand successors
		Pos[] adjacentCells = {
		    new Pos(1,0 ), // east
		    new Pos(-1,0 ), // west
		    new Pos(0,1 ), // north
		    new Pos(0,-1 ) // south
		};
		PathSearchNode[] newNodes = {
		     new PathSearchNode(AgentAction.MOVE_EAST, currentNode.pathCost+1,
					manhattanDist(adjacentCells[0], goal), adjacentCells[0], currentNode),
		     new PathSearchNode(AgentAction.MOVE_WEST, currentNode.pathCost+1,
					manhattanDist(adjacentCells[1],goal), adjacentCells[1], currentNode),
		     new PathSearchNode(AgentAction.MOVE_NORTH, currentNode.pathCost+1,
					manhattanDist(adjacentCells[2],goal), adjacentCells[2], currentNode),
		     new PathSearchNode(AgentAction.MOVE_SOUTH, currentNode.pathCost+1,
					manhattanDist(adjacentCells[3],goal), adjacentCells[3], currentNode),
		};
		// shuffle the order
		if(random){
		    
		}
		for(int i = 0; i<newNodes.length; i++){
		    Pos temp = newNodes[i].getPos();
		    // HomeBase is an obstacle unless the agent has the enemy flag.
		    // Don't add to heap if temp is homeBase unless the goal is homeBase.
		    if( ( hasFlag || !temp.equals(homeBase) ) &&
			// Don't add to heap if there's a wall or agent in the way
			!currentMap.testTeammate(temp, true) /*&& testEnemy(temp)!=-1.0 */ &&
			!currentMap.isBlocked(temp, true)) {
			
			heap.add(newNodes[i]);
		    }
		}
	    }
	}
	if(debug) System.out.println("\nERROR: Search Failed");
	return new PathSearchNode(AgentAction.DO_NOTHING, 0, 0, start, null);

    }
    
    private int moveTowards(Pos goal, boolean hasFlag){
	PathSearchNode currentNode = getPath(currentPos, goal, hasFlag, false);
	intention = currentNode;
	while(currentNode.getParent()!=null &&
	      currentNode.getParent().getParent()!=null){
	    // save the path for printing & debugging
	    currentNode = currentNode.getParent();
	    // if(debug) debugPathGrid[currentNode.getPos().x][currentNode.getPos().y] = true;
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

    private boolean leftStart(){
	return startCorner==NORTH_WEST_START || startCorner==SOUTH_WEST_START;
    }

    private boolean rightStart(){
	return startCorner==NORTH_EAST_START || startCorner==SOUTH_EAST_START;
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

    private boolean onHomeCol(AgentEnvironment env){
	// Returns true if home base is not to the east or west
	return !( env.isBaseWest(env.OUR_TEAM, false) || env.isBaseEast(env.OUR_TEAM, false) );
    }
    
    // implements Agent.getMove() interface
    private void createMaps(){
	int mapWidth = northTravelDist + southTravelDist + 1;
	completeMap = new CompleteGrid(mapWidth);

	DynamicGrid otherMap = getTeammate().dynamicMap;
	for(int x = 1; x < mapWidth-1; x++){
	    for (int y = 0; y<mapWidth; y++){
		Pos p = new Pos(x,y);
		int status = dynamicMap.testGlobalObstacle(p);
		if(status == UNEXPLORED){
		    status = otherMap.testGlobalObstacle(p);
		}
		completeMap.insertObstacle(p, status, false);
	    }
	}
	if(debug) System.out.println("Maps Merged!");
	for(int i = 0; i< mapWidth; i++){
	    completeMap.insertObstacle(new Pos(0,i),EMPTY, false);
	    completeMap.insertObstacle(new Pos(mapWidth-1, i), EMPTY, false);
	}
	
	setGlobalPos();
	getTeammate().setGlobalPos();

	
    }


    private void insertMapData(){
	// localMap
	// bomb map
	// agent map
	// chokemap
    }
	
    private void setGlobalPos(){
	//globalPos = LocalMap.getGlobalPos(loca);
    }

    /**
     * PRECONDITION: on corner of home column
     */
    public void setStartCorner(AgentEnvironment e){
	if(e.isObstacleNorthImmediate()){
	    if(e.isObstacleWestImmediate()){
		startCorner = NORTH_WEST_START;
	    }else if(e.isObstacleEastImmediate()){
		startCorner = NORTH_EAST_START;
	    }else{
		if(debug)
		    System.out.println("ERROR: updating start corner while not on home corner");
	    }
	}else if(e.isObstacleSouthImmediate()){
	    if(e.isObstacleWestImmediate()){
		startCorner = SOUTH_WEST_START;
	    }else if(e.isObstacleEastImmediate()){
		startCorner = SOUTH_EAST_START;
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
	    if(teammate.currentPos==null){
		agent1.mode = ATTACK;
		agent2.mode = DEFEND;
		return;
	    }
	    int myDist = this.pathLength(this.getPath(currentPos, enemyBase, false, false));
	    int otherDist = teammate.pathLength( teammate.getPath(teammate.currentPos,enemyBase, false, false));
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
	    // if(isChoke(currentPos) && (Math.random() > 0.925)){
	    // 	return PLANT_MINE;
	    // }else{
	    // 	return moveTowards(enemyBase,false);			
	    // }
	}
    }
    
    public int defenseModeMove(){
	if(doneExploring){
	    if(debug) System.out.println("Done Exploring.");
	    // 	    currentMap.weightChokepoints();
	    if(debug) currentMap.printChokeMap();
	    // find a chokepoint to guard
	    Pos bestChoke = currentMap.findBestChoke();	    
	    // move towards that chokepoint
	    return moveTowards(bestChoke, false);// if we're in defense mode then we don't have the flag
	}else{
	    // explore the region around home base
	    if(debug) System.out.println("Eploring region.");
	    doneExploring = true;
	    return defenseModeMove();
	}	
    }

    public int getMove( AgentEnvironment env ) {
	if (debug) System.out.println("***** Processing Agent "+ID+" *****");

	currentMap.update(env);
	
	/** Check if the agent has been tagged or exploded */
	if((env.isObstacleNorthImmediate() || env.isObstacleSouthImmediate()) && onHomeCol(env)){
	    //prevPos = currentPos.clone();
	    //currentMap.setAgent(prevPos, 0);
	    if(currentMap instanceof DynamicGrid){
		currentPos = new Pos(0, 0);
	    }else{
		switch(startCorner){
		case NORTH_WEST_START:
		    currentPos.x = 0;
		    currentPos.y = completeMap.width()-1;
		    break;
		case NORTH_EAST_START:
		    currentPos.x = completeMap.width()-1;
		    currentPos.y = completeMap.width()-1;
		    break;
		case SOUTH_WEST_START:
		    currentPos.x = 0;
		    currentPos.y = 0;
		    break;
		case SOUTH_EAST_START:
		    currentPos.x = completeMap.width()-1;
		    currentPos.y = 0;
		    break;
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
	    currentMap.printObstacleMap();
	    // currentMap.printChokeMap();
	    // currentMap.printChokeWeights();
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
