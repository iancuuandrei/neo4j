package p7mat;
import java.nio.file.*;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseInternalSettings.StatefulShortestPlanningMode;
import org.neo4j.dbms.api.*;
import org.neo4j.graphdb.Result;
public final class P10GuardCheck {
  public static void main(String[] a) throws Exception {
    var dir=Files.createTempDirectory("g");
    var db=new DatabaseManagementServiceBuilder(dir).setConfig(GraphDatabaseInternalSettings.stateful_shortest_planning_mode, StatefulShortestPlanningMode.INTO_ONLY).build();
    try{
      try(var tx=db.database("neo4j").beginTx()){tx.execute("CREATE (:N{id:0}), (:N{id:1})").close();tx.execute("MATCH (x:N{id:0}), (y:N{id:1}) CREATE (x)-[:R]->(y)").close();tx.commit();}
      check(db,"MATCH (a:N{id:0}), (b:N{id:1}) MATCH ANY SHORTEST (a) ((c)-[r:R]-(d))+ (b) RETURN c");
      check(db,"MATCH (a:N{id:0}), (b:N{id:1}) MATCH ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN c");
      // pre-existing: undirected group-free must already be FSP (proves unsafe region pre-exists)
      check(db,"MATCH (a:N{id:0}), (b:N{id:1}) MATCH ANY SHORTEST (a) ((c)-[r:R]-(d))+ (b) RETURN r");
    }finally{db.shutdown();}
  }
  static void check(DatabaseManagementService db,String q){
    try(var tx=db.database("neo4j").beginTx()){
      try(Result r=tx.execute("EXPLAIN "+q)){
        var d=r.getExecutionPlanDescription();
        System.out.println("Q: "+q);
        print(d,0);
        System.out.println("hasSSP="+contains(d,"StatefulShortestPath"));
      }
      tx.commit();
    }
  }
  static boolean contains(org.neo4j.graphdb.ExecutionPlanDescription d,String s){if(d.getName().contains(s))return true;for(var c:d.getChildren())if(contains(c,s))return true;return false;}
  static void print(org.neo4j.graphdb.ExecutionPlanDescription d,int i){System.out.println("  ".repeat(i)+d.getName()+" "+d.getArguments());for(var c:d.getChildren())print(c,i+1);}
}
