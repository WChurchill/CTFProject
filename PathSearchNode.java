    /**
     * Checks the map and calculates the quickest route to the specified base
     * returns the best direction
     *
     */

public class PathSearchNode implements Comparable<PathSearchNode>{
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
