import java.io.FileNotFoundException;
import java.util.Scanner;
import java.io.File;

class ChokeTester
{
    private static int[][] map;
    private static final int BLOCKED = 0;
    private static final int EMPTY = 1;
    private static final int UNEXPLORED = 2;

    private static void parseMap(String fileName) throws FileNotFoundException{
	Scanner in = null;
	in = new Scanner(new File(fileName));
	int mapWidth =  in.nextInt();
	map = new int[mapWidth][mapWidth];
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

    private static void printMap() {
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

    public static void main(String args[]) {
	int map[][];
	try{
	    parseMap("chokepoints.txt");
	    printMap();
	}catch(FileNotFoundException e){
	    e.printStackTrace();
	}
	
    }
}
