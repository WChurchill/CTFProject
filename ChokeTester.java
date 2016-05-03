package ctf.agent;

import java.io.FileNotFoundException;
import java.util.Scanner;
import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;

class ChokeTester {
    private int[][] map;
    private final int BLOCKED = 0;
    private final int EMPTY = 1;
    private final int UNEXPLORED = 2;

    private int[][] chokeMap;
    private final int TOPLEFT = -1;
    private final int TOPRIGHT = -2;
    private final int VERTICAL = -3;
    private final int HORIZONTAL = -4;
    private final int CROSS = -5;
    private final int DIAG = -6;
    

    private void parseMap(String fileName) throws FileNotFoundException{
	Scanner in = null;
	in = new Scanner(new File(fileName));
	int mapWidth =  in.nextInt();
	map = new int[mapWidth][mapWidth];
	chokeMap = new int[mapWidth][mapWidth];
	for(int row = mapWidth-1; row>=0; row--){
	    for (int col = 0; col<mapWidth; col++) {
		int status = 0;
		switch(in.next()){
		case "1":
		    status = BLOCKED;
		    break;
		case "?":
		    status = UNEXPLORED;
		    break;
		case "0":
		    status = EMPTY;
		    break;
		default:
		    
		}
		map[col][row] = status;
	    }
	}
	in.close();
    }

    private void randomMap(String name, int width) throws IOException{
	int temp[][] = new int[width][width];
	PrintWriter writer = new PrintWriter(new File(name));
	writer.write(width+"\n");
	for(int y = width-1; y>=0; y--){
	    writer.write("0 ");
	    for(int x = 1; x<width-1; x++){
		if(Math.random()>0.35)
		    writer.write("0 ");
		else
		    writer.write("1 ");
	    }
	    writer.write("0\n");
	}
	writer.close();
    }
    
    private int isW1ChokePoint(Pos testPos){
	// check if the test position is a wall	
	if(testObstacle(testPos)){
	    return BLOCKED;
	}
	// indicates the orientation of the "hallway"
	boolean verticalChoke = normalChoke(testPos, VERTICAL); // open spaces north and south
	boolean horizontalChoke = normalChoke(testPos, HORIZONTAL); // open spaces east and west
	boolean topRightChoke = normalChoke(testPos, TOPRIGHT); // top left and bottom right blocked
	boolean topLeftChoke = normalChoke(testPos, TOPLEFT); // top right and bottom left blocked

	if(topRightChoke && topLeftChoke){
	    return DIAG;
	}else if (verticalChoke && horizontalChoke){
	    return CROSS;
	}else if(horizontalChoke){
	    return HORIZONTAL;
	}else if(verticalChoke){
	    return VERTICAL;
	}else if(topRightChoke){
	    return TOPRIGHT;
	}else if(topLeftChoke){
	    return TOPLEFT;
	}
	return EMPTY;
    }
    
    private void printChokeMap(){
	int width = map.length;
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
		    if(map[column][row]==BLOCKED){
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

    private boolean normalChoke(Pos testPos, int orientation){
	int testX = testPos.x;
	int testY = testPos.y;
	
	switch(orientation){
	case VERTICAL:
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
		    testObstacle(new Pos(testX+1, testY))  ||
		    testObstacle(new Pos(testX+1, testY-1)))){
		    return true;
		}else if(testObstacle(new Pos(testX+1, testY)) && // test if the east cell is blocked
			 // test if the northWest, west, or southWest cells are blocked
			 (testObstacle(new Pos(testX-1, testY+1))|| 
			  testObstacle(new Pos(testX-1, testY)) ||
			  testObstacle(new Pos(testX-1, testY-1)))){
		    return true;
		}
	    }
	    return false;
	case HORIZONTAL:
	    /*
	     * Check for horizontal corridor, i.e. walls on the north and south 
	     * but not directly east or directly west
	     */
	    if( !(testObstacle(new Pos(testX-1, testY)) || testObstacle(new Pos(testX+1, testY)) ) ){
		// test if the north cell is blocked
		if(testObstacle(new Pos(testX, testY+1)) && 
		   // test if the southEast, south, or southWest cells are blocked
		   (testObstacle(new Pos(testX-1, testY-1)) || 
		    testObstacle(new Pos(testX,   testY-1)) ||
		    testObstacle(new Pos(testX+1, testY-1)))){
		    return true;
		}else if(testObstacle(new Pos(testX, testY-1)) && // test if the south cell is blocked
			 // test if the northEast, north, or northWest cells are blocked
			 (testObstacle(new Pos(testX-1, testY+1)) || 
			  testObstacle(new Pos(testX,   testY+1)) ||
			  testObstacle(new Pos(testX+1, testY+1)))){
		    return true;
		}
	    }
	    return false;
	case TOPRIGHT:
	    
	    /*
	     * Check for topRightChoke
	     * topLeft cell blocked and bottomRight cell blocked
	     */
	    if( testObstacle(new Pos(testX-1,testY+1)) && testObstacle(new Pos(testX+1,testY-1)) && 
		!testObstacle(new Pos(testX-1, testY-1)) && !testObstacle(new Pos(testX+1,testY+1))){
		    return true;
		}
	    return false;
	
	case TOPLEFT:
	    /*
	     * Check for topLeftChoke
	     * topRight cell blocked and bottomRight cell blocked
	     */
	    if( testObstacle(new Pos(testX-1,testY+1)) && testObstacle(new Pos(testX+1,testY-1)) &&
		!testObstacle(new Pos(testX-1, testY-1)) && !testObstacle(new Pos(testX+1,testY+1))){
		return true;
	    }

	    return false;
	}
	return false;
    }

    
    private void doChokePoints(){
	for(int x = 0; x<chokeMap.length; x++){
	    for(int y = 0; y<chokeMap.length; y++){
		chokeMap[x][y] = isW1ChokePoint(new Pos(x,y));
	    }
	}
    }
    
    private void printMap() {
	int width = map.length;
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
		switch(map[column][row]){
		case BLOCKED:
		    System.out.print("[]");
		    break;
		case UNEXPLORED:
		    System.out.print("??");
		    break;
		case EMPTY:
		    System.out.print("  ");
		    break;
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
    
    private boolean testObstacle(Pos p){
	try{
	    return map[p.x][p.y]==BLOCKED;
	}catch(IndexOutOfBoundsException e){
	    return false;
	}
    }

    public ChokeTester(){
	int map[][];
	String names[] = { "chokepoints",
			 "genTest",
			 "empty",
			 "labyrinth",
			 "maze",
			 "simple",
			 "test",
			 "traps",
			 "wall",
			 "x"
	};
	try{
	    for(int i = 0; i<names.length; i++){
		String name = names[i]+".txt";
		parseMap(name);
		doChokePoints();
		printMap();
		printChokeMap();
	    }
	}catch(IOException e){
	    e.printStackTrace();
	}
    }
    
    
    public static void main(String args[]) {
	ChokeTester c = new ChokeTester();
    }
}
