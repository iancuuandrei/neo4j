package p7mat;

import java.nio.file.*;
import java.util.*;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseInternalSettings.StatefulShortestPlanningMode;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.ExecutionPlanDescription;

/** Patched-build planner qualification: 9-case table. Uses patched classes (must run with patched cp). */
public final class P10Qual {
  static DatabaseManagementService db;
  static int pass=0, fail=0;
  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("p10qual");
    System.out.println("DBDIR="+dir);
    db = new DatabaseManagementServiceBuilder(dir)
      .setConfig(GraphDatabaseInternalSettings.stateful_shortest_planning_mode, StatefulShortestPlanningMode.INTO_ONLY)
      .build();
    try {
      setup();
      // positive: node groups must now be FSP
      expectFSP("left-group", "MATCH (a:N{id:0}), (b:N{id:3}) MATCH ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN c");
      expectFSP("right-group", "MATCH (a:N{id:0}), (b:N{id:3}) MATCH ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN d");
      expectFSP("both-groups", "MATCH (a:N{id:0}), (b:N{id:3}) MATCH ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN c, d");
      expectFSP("rel+node-groups", "MATCH (a:N{id:0}), (b:N{id:3}) MATCH ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN c, d, r");
      expectFSP("path+groups", "MATCH (a:N{id:0}), (b:N{id:3}) MATCH p = ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN p, c, d, r");
      // negative: must stay SSP
      expectSSP("multi-rel", "MATCH (a:N{id:0}), (b:N{id:3}) MATCH p = ANY SHORTEST (a) ((x)-[r:R]->(y)-[r2:R]->(z))+ (b) RETURN p");
      expectSSP("min>1", "MATCH (a:N{id:0}), (b:N{id:3}) MATCH p = ANY SHORTEST (a)-[r:R*4]->(b) RETURN p");
      expectSSP("k>1", "MATCH (a:N{id:0}), (b:N{id:3}) MATCH p = SHORTEST 5 GROUPS (a)-[r:R*1..10]->(b) RETURN p");
      // stacked: must be FSP+FSP on patched
      expectFSPFSP("stacked", "MATCH (a:N{id:0}), (b:N{id:3}) WITH * MATCH p1 = ANY SHORTEST (a)-[r1:R*1..10]->(b) MATCH p2 = ANY SHORTEST (a)-[r2:R*1..10]->(b) RETURN p1, p2");
      System.out.println("QUAL pass="+pass+" fail="+fail);
      if (fail>0) System.exit(1);
      System.out.println("PLANNER QUALIFICATION PASSED");
    } finally { db.shutdown(); }
  }
  static void setup() {
    try (var tx = db.database("neo4j").beginTx()) {
      tx.execute("MATCH (n) DETACH DELETE n").close();
      tx.execute("CREATE (a:N{id:0}), (b:N{id:1}), (c:N{id:2}), (d:N{id:3})").close();
      tx.execute("MATCH (a:N{id:0}), (b:N{id:1}) CREATE (a)-[:R]->(b)").close();
      tx.execute("MATCH (b:N{id:1}), (c:N{id:2}) CREATE (b)-[:R]->(c)").close();
      tx.execute("MATCH (c:N{id:2}), (d:N{id:3}) CREATE (c)-[:R]->(d)").close();
      tx.execute("MATCH (a:N{id:0}), (c:N{id:2}) CREATE (a)-[:R]->(c)").close();
      tx.commit();
    }
  }
  static ExecutionPlanDescription plan(String q) {
    try (var tx = db.database("neo4j").beginTx()) {
      try (Result r = tx.execute("EXPLAIN "+q)) { var d=r.getExecutionPlanDescription(); tx.commit(); return d; }
    }
  }
  static boolean contains(ExecutionPlanDescription d, String op) {
    if (d.getName().contains(op)) return true;
    for (var c: d.getChildren()) if (contains(c,op)) return true;
    return false;
  }
  static void print(ExecutionPlanDescription d,int i){ System.out.println("  ".repeat(i)+d.getName()+" "+d.getArguments()); for(var c:d.getChildren()) print(c,i+1); }
  static void expectFSP(String name,String q){
    var d=plan(q);
    System.out.println("=== "+name+" (expect FSP) ===\n"+q); print(d,0);
    boolean hasSSP=contains(d,"StatefulShortestPath");
    boolean hasFSP=contains(d,"ShortestPath") && !hasSSP || (contains(d,"ShortestPath") && count(d,"ShortestPath")>count(d,"StatefulShortestPath"));
    // precise: must contain ShortestPath operator and no StatefulShortestPath
    boolean ok = contains(d,"ShortestPath") && !hasSSP;
    System.out.println("hasSSP="+hasSSP+" hasShortestPath="+contains(d,"ShortestPath")+" => "+(ok?"PASS":"FAIL"));
    if(ok)pass++;else fail++;
    if(q.contains("RETURN c") && !q.contains("nodes(")) System.out.println("NOTE: query returns native group(s) with no nodes() slices; FSP values must come from new output fields.");
  }
  static void expectSSP(String name,String q){
    var d=plan(q);
    System.out.println("=== "+name+" (expect SSP) ==="); print(d,0);
    boolean ok=contains(d,"StatefulShortestPath");
    System.out.println("hasSSP="+ok+" => "+(ok?"PASS":"FAIL"));
    if(ok)pass++;else fail++;
  }
  static void expectFSPFSP(String name,String q){
    var d=plan(q);
    System.out.println("=== "+name+" (expect FSP+FSP) ==="); print(d,0);
    int fsp=count(d,"ShortestPath"), ssp=count(d,"StatefulShortestPath");
    // count ShortPath includes SSP names containing ShortestPath; subtract
    int pureFSP=fsp-ssp;
    boolean ok=(ssp==0 && pureFSP==2);
    System.out.println("SSP="+ssp+" pureFSP="+pureFSP+" => "+(ok?"PASS":"FAIL"));
    if(ok)pass++;else fail++;
  }
  static int count(ExecutionPlanDescription d,String s){ int n=d.getName().contains(s)?1:0; for(var c:d.getChildren()) n+=count(c,s); return n; }
}
