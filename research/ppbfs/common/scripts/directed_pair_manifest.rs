//! Deterministically select directed BFS pairs from a numeric relationship CSV.

use std::collections::VecDeque;
use std::env;
use std::fs::File;
use std::io::{BufRead, BufReader, BufWriter, Write};

fn usage() -> ! {
    eprintln!("usage: directed_pair_manifest <relationships.csv> <manifest.csv> <survey.txt> <seed> <samples> <distances> <pairs_per_distance> [selected|all]");
    std::process::exit(2);
}

fn parse_edge(line: &str) -> (usize, usize) {
    let mut fields = line.split(',');
    let source = fields.next().unwrap().parse().unwrap();
    let target = fields.next().unwrap().parse().unwrap();
    (source, target)
}

fn bfs(offsets: &[usize], targets: &[u32], source: usize) -> Vec<i32> {
    let mut distance = vec![-1_i32; offsets.len() - 1];
    if source >= distance.len() { return distance; }
    let mut queue = VecDeque::new();
    distance[source] = 0;
    queue.push_back(source);
    while let Some(node) = queue.pop_front() {
        let next = distance[node] + 1;
        for &target in &targets[offsets[node]..offsets[node + 1]] {
            let target = target as usize;
            if distance[target] == -1 {
                distance[target] = next;
                queue.push_back(target);
            }
        }
    }
    distance
}

fn main() -> std::io::Result<()> {
    let args: Vec<String> = env::args().collect();
    if args.len() != 8 && args.len() != 9 { usage(); }
    let input = &args[1];
    let manifest = &args[2];
    let survey = &args[3];
    let mut random: u64 = args[4].parse().unwrap();
    let samples: usize = args[5].parse().unwrap();
    let requested: Vec<i32> = args[6].split(',').map(|x| x.parse().unwrap()).collect();
    let pairs_per_distance: usize = args[7].parse().unwrap();
    let mode = args.get(8).map(String::as_str).unwrap_or("selected");
    if mode != "selected" && mode != "all" { usage(); }

    let mut max_node = 0_usize;
    let mut edges = 0_usize;
    for line in BufReader::new(File::open(input)?).lines() {
        let (source, target) = parse_edge(&line?);
        max_node = max_node.max(source).max(target);
        edges += 1;
    }
    let mut degrees = vec![0_usize; max_node + 1];
    for line in BufReader::new(File::open(input)?).lines() {
        let (source, _) = parse_edge(&line?);
        degrees[source] += 1;
    }
    let mut offsets = vec![0_usize; max_node + 2];
    for node in 0..=max_node { offsets[node + 1] = offsets[node] + degrees[node]; }
    let mut cursors = offsets[..=max_node].to_vec();
    let mut targets = vec![0_u32; edges];
    for line in BufReader::new(File::open(input)?).lines() {
        let (source, target) = parse_edge(&line?);
        targets[cursors[source]] = target as u32;
        cursors[source] += 1;
    }

    let active_sources: Vec<usize> = degrees.iter().enumerate().filter_map(|(node, &degree)| (degree > 0).then_some(node)).collect();
    let mut candidates = Vec::with_capacity(samples);
    for _ in 0..samples {
        random = random.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
        candidates.push(active_sources[(random as usize) % active_sources.len()]);
    }
    let mut survey_output = BufWriter::new(File::create(survey)?);
    writeln!(survey_output, "node_id_range={} active_sources={} edges={} seed={} samples={}", max_node + 1, active_sources.len(), edges, args[4], samples)?;
    let mut best: Option<(usize, usize, i32, Vec<i32>)> = None;
    let mut all_pairs: Vec<(usize, usize, i32)> = Vec::new();
    for source in candidates {
        let distance = bfs(&offsets, &targets, source);
        let reachable = distance.iter().filter(|&&d| d >= 0).count();
        let maximum = *distance.iter().max().unwrap_or(&-1);
        writeln!(survey_output, "source={} reachable={} max_distance={}", source, reachable, maximum)?;
        if mode == "all" {
            for &requested_distance in &requested {
                let mut count = 0;
                for (target, &actual) in distance.iter().enumerate() {
                    if actual == requested_distance && count < pairs_per_distance {
                        all_pairs.push((source, target, actual));
                        count += 1;
                    }
                }
                writeln!(survey_output, "source={} requested_distance={} retained={}", source, requested_distance, count)?;
            }
        }
        if best.as_ref().map_or(true, |(_, r, m, _)| (maximum, reachable) > (*m, *r)) {
            best = Some((source, reachable, maximum, distance));
        }
    }
    let (source, reachable, maximum, distance) = best.unwrap();
    writeln!(survey_output, "selected_source={} reachable={} max_distance={}", source, reachable, maximum)?;
    let mut output = BufWriter::new(File::create(manifest)?);
    writeln!(output, "source,target,measured_distance")?;
    if mode == "all" {
        for (source, target, actual) in all_pairs {
            writeln!(output, "{},{},{}", source, target, actual)?;
        }
        return Ok(());
    }
    for requested_distance in requested {
        let mut count = 0;
        for (target, &actual) in distance.iter().enumerate() {
            if actual == requested_distance && count < pairs_per_distance {
                writeln!(output, "{},{},{}", source, target, actual)?;
                count += 1;
            }
        }
        writeln!(survey_output, "requested_distance={} retained={}", requested_distance, count)?;
    }
    Ok(())
}
