package p7mat;

import java.nio.file.*;
import java.util.*;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseInternalSettings.StatefulShortestPlanningMode;
import org.neo4j.dbms.api.*;
import org.neo4j.graphdb.Result;

/**
 * Paired-fork bench: one JVM invocation = one fork.
 * Builds chain/tree/diamond, then for each workload measures SSP (flag OFF) vs FSP (flag ON)
 * on SAME patched build, SAME query, SAME data (two DBMS, same setup).
 * Prints per-workload avg ms and ratio R=T_SSP/T_FSP.
 * Run 10x as separate JVM processes for paired CIs.
 */
public final class P10Paired {
  public static void main(String[] args) throws Exception {
    int warmup = args.length>0?Integer.parseInt(args[0]):100;
    int measure = args.length>1?Integer.parseInt(args[1]):200;
    Path d1=Files.createTempDirectory("ssp"), d2=Files.createTempDirectory("fsp");
    var dbSSP=new DatabaseManagementServiceBuilder(d1)
      .setConfig(GraphDatabaseInternalSettings.gpm_shortest_to_legacy_shortest_enabled,false).build();
    var dbFSP=new DatabaseManagementServiceBuilder(d2)
      .setConfig(GraphDatabaseInternalSettings.gpm_shortest_to_legacy_shortest_enabled,true)
      .setConfig(GraphDatabaseInternalSettings.stateful_shortest_planning_mode,StatefulShortestPlanningMode.INTO_ONLY).build();
    try{
      // workloads: chain16, chain64, tree, diamond
      benchFamily(dbSSP,dbFSP,"chain16",()->{setupChain(dbSSP,16);setupChain(dbFSP,16);},
        "MATCH (a:N{id:0}), (b:N{id:15}) MATCH p = ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN c, d, r",warmup,measure);
      benchFamily(dbSSP,dbFSP,"chain64",()->{setupChain(dbSSP,64);setupChain(dbFSP,64);},
        "MATCH (a:N{id:0}), (b:N{id:63}) MATCH p = ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN c, d, r",warmup,measure);
      benchFamily(dbSSP,dbFSP,"tree-b4-d6",()->{setupTree(dbSSP,4,6);setupTree(dbFSP,4,6);},
        "MATCH (a:N{id:0}), (b:N{id:9999}) MATCH p = ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN c, d, r",warmup,measure);
      benchFamily(dbSSP,dbFSP,"diamond",()->{setupDiamond(dbSSP);setupDiamond(dbFSP);},
        "MATCH (a:N{id:0}), (b:N{id:99}) MATCH p = SHORTEST GROUP (a) ((c)-[r:R]->(d))+ (b) RETURN c, d, r",warmup,measure);
      // A: stacked
      benchFamily(dbSSP,dbFSP,"stacked",()->{setupChain(dbSSP,32);setupChain(dbFSP,32);},
        "MATCH (a:N{id:0}), (b:N{id:31}) WITH * MATCH p1 = ANY SHORTEST (a)-[r1:R*1..20]->(b) MATCH p2 = ANY SHORTEST (a)-[r2:R*1..20]->(b) RETURN p1, p2",warmup,measure);
      // tax: FSP RETURN p vs RETURN p,groups (both FSP on dbFSP)
      tax(dbFSP,warmup,measure);
      // regression: existing FSP (no groups) SSP-flag-off vs ON should both be FSP? Actually flag off forces SSP even for eligible? For var-length no-group query, flag off → SSP, flag on → FSP. That's B-like, not regression. True regression: query that is FSP in both (e.g., legacy shortestPath() function, not GPM) – unaffected by flag. Use shortestPath() function:
      benchFamily(dbSSP,dbFSP,"regress-legacy",
        ()->{setupChain(dbSSP,16);setupChain(dbFSP,16);},
        "MATCH (a:N{id:0}), (b:N{id:15}) MATCH p = shortestPath((a)-[r:R*1..10]->(b)) RETURN p",warmup,measure);
    }finally{dbSSP.shutdown();dbFSP.shutdown();}
  }
  static void benchFamily(DatabaseManagementService s,DatabaseManagementService f,String name,Runnable setup,String q,int w,int m){
    setup.run();
    // verify plans once
    String ps=plan(s,q), pf=plan(f,q);
    boolean sSSP=ps.contains("StatefulShortestPath"), fSSP=pf.contains("StatefulShortestPath");
    System.out.println("["+name+"] SSP-plan-SSP="+sSSP+" FSP-plan-SSP="+fSSP+" (want true/false for B/A; for regress both FSP or both SSP)");
    for(int i=0;i<w;i++){run(s,q);run(f,q);}
    long tS=0,tF=0;
    for(int i=0;i<m;i++){long t=System.nanoTime();run(s,q);tS+=System.nanoTime()-t;}
    for(int i=0;i<m;i++){long t=System.nanoTime();run(f,q);tF+=System.nanoTime()-t;}
    double aS=tS/1e6/m, aF=tF/1e6/m, r=aS/aF;
    System.out.printf("[%s] SSP=%.3fms FSP=%.3fms R=%.3f%n",name,aS,aF,r);
  }
  static void tax(DatabaseManagementService db,int w,int m){
    setupChain(db,64);
    String p="MATCH (a:N{id:0}), (b:N{id:63}) MATCH p = ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN p";
    String pg1="MATCH (a:N{id:0}), (b:N{id:63}) MATCH p = ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN p, c";
    String pg2="MATCH (a:N{id:0}), (b:N{id:63}) MATCH p = ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN p, c, d, r";
    for(int i=0;i<w;i++){run(db,p);run(db,pg1);run(db,pg2);}
    long t0=0,t1=0,t2=0;
    for(int i=0;i<m;i++){long t=System.nanoTime();run(db,p);t0+=System.nanoTime()-t;}
    for(int i=0;i<m;i++){long t=System.nanoTime();run(db,pg1);t1+=System.nanoTime()-t;}
    for(int i=0;i<m;i++){long t=System.nanoTime();run(db,pg2);t2+=System.nanoTime()-t;}
    System.out.printf("[tax] p=%.3fms p+c=%.3fms (+%.1f%%) p+c+d+r=%.3fms (+%.1f%%)%n",t0/1e6/m,t1/1e6/m,100.0*(t1-t0)/t0,t2/1e6/m,100.0*(t2-t0)/t0);
  }
  static void setupChain(DatabaseManagementService db,int n){
    try(var tx=db.database("neo4j").beginTx()){
      tx.execute("MATCH (x) DETACH DELETE x").close();
      for(int i=0;i<n;i++)tx.execute("CREATE (:N{id:"+i+"})").close();
      for(int i=0;i<n-1;i++)tx.execute("MATCH (a:N{id:"+i+"}), (b:N{id:"+(i+1)+"}) CREATE (a)-[:R]->(b)").close();
      for(int i=0;i<n-10;i+=10)tx.execute("MATCH (a:N{id:"+i+"}), (b:N{id:"+(i+10)+"}) CREATE (a)-[:R]->(b)").close();
      tx.commit();
    }
  }
  static void setupTree(DatabaseManagementService db,int b,int depth){
    try(var tx=db.database("neo4j").beginTx()){
      tx.execute("MATCH (x) DETACH DELETE x").close();
      tx.execute("CREATE (:N{id:0})").close();
      int next=1;
      List<Integer> frontier=new ArrayList<>(List.of(0));
      for(int d=0;d<depth;d++){
        List<Integer> nf=new ArrayList<>();
        for(int p: frontier){
          for(int k=0;k<b;k++){
            int id=next++;
            tx.execute("CREATE (:N{id:"+id+"})").close();
            tx.execute("MATCH (a:N{id:"+p+"}), (x:N{id:"+id+"}) CREATE (a)-[:R]->(x)").close();
            nf.add(id);
          }
        }
        frontier=nf;
      }
      tx.execute("CREATE (:N{id:9999})").close();
      for(int leaf: frontier) tx.execute("MATCH (a:N{id:"+leaf+"}), (b:N{id:9999}) CREATE (a)-[:R]->(b)").close();
      tx.commit();
    }
  }
  static void setupDiamond(DatabaseManagementService db){
    try(var tx=db.database("neo4j").beginTx()){
      tx.execute("MATCH (x) DETACH DELETE x").close();
      tx.execute("CREATE (:N{id:0}), (:N{id:99})").close();
      int id=1, prev=0;
      for(int l=0;l<6;l++){
        int a=id++,bb=id++;
        tx.execute("CREATE (:N{id:"+a+"}), (:N{id:"+bb+"})").close();
        int join=(l==5)?99:id++;
        if(l<5)tx.execute("CREATE (:N{id:"+join+"})").close();
        tx.execute("MATCH (p:N{id:"+prev+"}), (x:N{id:"+a+"}) CREATE (p)-[:R]->(x)").close();
        tx.execute("MATCH (p:N{id:"+prev+"}), (x:N{id:"+bb+"}) CREATE (p)-[:R]->(x)").close();
        tx.execute("MATCH (x:N{id:"+a+"}), (j:N{id:"+join+"}) CREATE (x)-[:R]->(j)").close();
        tx.execute("MATCH (x:N{id:"+bb+"}), (j:N{id:"+join+"}) CREATE (x)-[:R]->(j)").close();
        prev=join;
      }
      tx.commit();
    }
  }
  static void run(DatabaseManagementService db,String q){try(var tx=db.database("neo4j").beginTx()){try(var r=tx.execute(q)){while(r.hasNext())r.next();}tx.commit();}}
  static String plan(DatabaseManagementService db,String q){try(var tx=db.database("neo4j").beginTx()){try(var r=tx.execute("EXPLAIN "+q)){String s=r.getExecutionPlanDescription().toString();tx.commit();return s;}}}
  interface Runnable{void run();}
}
