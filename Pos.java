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
