private class LocalMap {
    // x/y role of each arraylist changes based on the starting
    // corner
    // The coordinates (0,0) indicate the starting position of the
    // agent regardless of orientation. 
    public int maxY = 3;
    public int maxX = 3;
    public ArrayList<ArrayList<Integer>> grid;

    public LocalMap(){
	grid = new ArrayList<>(3);
	for(int x = 0; x<maxX; x++){
	    ArrayList<Integer> column = new ArrayList<>(3);
	    for(int y = 0; y<maxY; y++){
		column.set(y, UNEXPLORED);
	    }
	    grid.set(x, column);
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

	if(grid.size()>x){
	    ArrayList<Integer> column = grid.get(x);
	    if(column.size()>y){
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
