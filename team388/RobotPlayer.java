package team388;

import battlecode.common.*;

public class RobotPlayer {
	/** 0:[North] 1:[North-East] 2:[East] 3:[South-East] 4:[South] 5:[South-West] 6:[West] 7:[North-West] **/
	static final Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST, Direction.NONE, Direction.OMNI};
	/** 0:[Archon] 1:[Soldier] 2:[Guard] 3:[Viper] 4:[Turret] 5:[TTM] 6:[Scout] **/
	static final RobotType[] robotTypes = {RobotType.ARCHON, RobotType.SOLDIER, RobotType.GUARD, RobotType.VIPER, RobotType.TURRET, RobotType.TTM, RobotType.SCOUT};
	static Team[] teams = Team.values();
	static RobotType botType;
	static RobotController rc;
	static Signal[] messages;

	/** Pathing related variables **/
	static MapLocation location;
	static Direction direction;
	static MapLocation[] map;
	static int distanceToGoal;
	static int[] candidates = new int[3];
	static int[] lastDirs = new int[3];
	static int lastDirsIndex = 0;
	static int[] directionList = new int[150];
	static int directionListIndex = 0;
	static MapLocation spawnPoint;
	static MapLocation corner;

	//------------- Archon only variables -------------//
	/** This Archon is the designated king of the pack **/
	static boolean isLeader;
	/** 0:[Game Initiation] 1:[Drones Constructed] 2:[Guard Force Constructed] 3:[Base Location Obtained] 4:[Reinforcements Constructed] **/
	static int flagArchon = 0;
	/** The number of guards left to build before moving to stage 3 **/
	static int guardCount = 3;

	//------------- Soldier/Guard only variables -------------//
	/** An array holding the data on nearby enemies **/
	static RobotInfo[] hostiles;
	/** The number of turns before updates to the hostiles list **/
	static int turnRecountDelay = 10;
	/** The maximum distance a combat unit will search for targets in **/
	static final int MAX_COMBAT_RANGE = 161;
	static int flagSoldier = 0;

	//------------- Scout only variables -------------//
	/** 0:[Locate Corners] 1:[Relay Corner Locations to Leader] 2:[Become Infected and Dive Bomb] **/
	static int flagScout = 0;
	static MapLocation[] scoutedLocations = new MapLocation[100];
	static int scoutedLocationsIndex = 0;
	static int[] edgeOffset = {1000, 1000, 1000, 1000};
	static int archonsNotified = 0;
	static MapLocation[] archonSpawns;

	/**
	 * run() is the method that is called when a robot is instantiated in the Battlecode world.
	 * If this method returns, the robot dies!
	 **/
	public static void run(RobotController rcIn) {
		try {
			rc = rcIn;
			botType = rc.getType();
			if (rc.getTeam() == teams[1]) {
				teams[0] = teams[1];
				teams[1] = Team.A;
			}
			location = rc.getLocation();
			direction = directions[4];
			directionList[0] = -1;
			directionListIndex = 1;
			spawnPoint = location;

			if (botType == robotTypes[0]) {
				if (rc.getRoundNum() == 0) {
					messages = rc.emptySignalQueue();
					if (messages.length == 0) {
						isLeader = true;
						rc.broadcastMessageSignal(6, 7, 100);
					}
				}
				while (true) {
					try {
						switch (flagArchon) {
						case 0: {
							// Build a 

							if (rc.isCoreReady()) {
								RobotInfo[] nearbyEnemies = rc.senseHostileRobots(rc.getLocation(), 25);
								if (nearbyEnemies.length > 0 && guardCount > 0) {
									if (rushBuild(robotTypes[2])) // build 2 guards asap
										guardCount--;
								}
								else {
									if (rushBuild(robotTypes[6])) // build a drone asap
										flagArchon++;
									else {
										// Check to see if there are neutrals, and if not, dig somewhere, and if can't, send arbitrary signals
										RobotInfo[] nearbyNeutrals = rc.senseNearbyRobots(location, 1, Team.NEUTRAL);
										if (nearbyNeutrals.length > 0) {
											rc.activate(nearbyNeutrals[0].location);
											if (nearbyNeutrals[0].type.compareTo(robotTypes[6]) == 0)
												flagArchon++;
										}
										else {
											int i = 0;
											while (i - 8 > 0) {
												MapLocation target = location.add(directions[i]);
												if (rc.senseRubble(target) > GameConstants.RUBBLE_OBSTRUCTION_THRESH)
													rc.clearRubble(directions[i]);
											}
										}
									}
								}
							}
						}; break;
						case 1: {
							if (rc.isCoreReady() && guardCount > 0) {
								rushBuild(robotTypes[1]);
								guardCount--;
							}

							Signal[] signals = rc.emptySignalQueue();
							for (int i = signals.length - 1; i >= 0; i--) {
								if (signals[i].getTeam().equals(teams[0])) {
									if (signals[i].getMessage() != null) {
										int[] message = signals[i].getMessage();
										if (corner == null)
											corner = new MapLocation(message[0], message[1]);
										else {
											MapLocation ml = new MapLocation(message[0], message[1]);
											if (location.distanceSquaredTo(corner) > location.distanceSquaredTo(ml))
												corner = ml;
										}
										flagArchon++;
										guardCount = 1;
										if (rc.isCoreReady()) {
											rc.broadcastMessageSignal(corner.x, corner.y, 161);
										}
									}
									else if (rc.isCoreReady() && signals[i].getTeam().equals(teams[0]))
										if (location.isAdjacentTo(signals[i].getLocation()))
											rc.repair(signals[i].getLocation());
										else if (location.distanceSquaredTo(signals[i].getLocation()) >= 9)
											guardCount++;
								}

							}

						}; break;
						case 2: {
							if (rc.isCoreReady() && rc.senseNearbyRobots(36, teams[0]).length < 5) {
								rushBuild(robotTypes[2]);
							}

							if (rc.isCoreReady()) {
								if (pathTo(corner)) {
									flagArchon++;
									if (rc.isCoreReady())
										rc.broadcastMessageSignal(corner.x, corner.y, 161);
								}
							}

						}; break;
						default: {
							if (rc.isCoreReady())
								rushBuild(robotTypes[1]);
						}
						}
						Clock.yield();
					} catch (Exception e) {
						System.out.println(e.getMessage());
						e.printStackTrace();
					}
				}
			} else if (botType != robotTypes[0] && botType != robotTypes[5] && botType != robotTypes[6]) {
				hostiles = rc.senseHostileRobots(location, botType.attackRadiusSquared);

				while (true) {
					try {

						if (flagSoldier == 0) {
							Signal[] signals = rc.emptySignalQueue();
							for (int i = 0; i < signals.length; i++)
								if (signals[i].getMessage() != null) {
									flagSoldier++;
									corner = new MapLocation(signals[i].getMessage()[0], signals[i].getMessage()[1]);
								}
						}

						if (flagSoldier > 0 && rc.isCoreReady()) {
							RobotInfo[] allies = rc.senseNearbyRobots(64, teams[0]);
							int archonID = -1;
							for (int i = 0; i < allies.length; i++)
								if (allies[i].type == robotTypes[0])
									archonID = i;
							if (archonID >= 0)
								pathTo(allies[archonID].location);
							else if (rc.canSense(corner.add(directions[1], 2)) && rc.onTheMap(corner.add(directions[1], 2)))
								pathTo(corner.add(directions[1], 2));
							else if (rc.canSense(corner.add(directions[3], 2)) && rc.onTheMap(corner.add(directions[3], 2)))
								pathTo(corner.add(directions[3], 2));
							else if (rc.canSense(corner.add(directions[5], 2)) && rc.onTheMap(corner.add(directions[5], 2)))
								pathTo(corner.add(directions[5], 2));
							else if (rc.canSense(corner.add(directions[7], 2)) && rc.onTheMap(corner.add(directions[7], 2)))
								pathTo(corner.add(directions[7], 2));
						}
						if (rc.isCoreReady() && rc.getHealth() < botType.maxHealth / 3) {
							if (flagSoldier == 0)
								if (pathTo(spawnPoint))
									rc.broadcastSignal(4);
								else {
									RobotInfo[] allies = rc.senseNearbyRobots(64, teams[0]);
									int archonID = -1;
									for (int i = 0; i < allies.length; i++)
										if (allies[i].type == robotTypes[0])
											archonID = i;
									if (corner != null) {
										if (archonID >= 0 && location.distanceSquaredTo(allies[archonID].location) >= 30)
											pathTo(allies[archonID].location);
										else if (rc.canSense(corner.add(directions[1], 2)) && rc.onTheMap(corner.add(directions[1], 2)))
											pathTo(corner.add(directions[1], 2));
										else if (rc.canSense(corner.add(directions[3], 2)) && rc.onTheMap(corner.add(directions[3], 2)))
											pathTo(corner.add(directions[3], 2));
										else if (rc.canSense(corner.add(directions[5], 2)) && rc.onTheMap(corner.add(directions[5], 2)))
											pathTo(corner.add(directions[5], 2));
										else if (rc.canSense(corner.add(directions[7], 2)) && rc.onTheMap(corner.add(directions[7], 2)))
											pathTo(corner.add(directions[7], 2));
									}
								}
						}

						hostiles = rc.senseHostileRobots(location, MAX_COMBAT_RANGE);
						if (rc.isWeaponReady()) {
							int botIndex = 0;
							if (hostiles.length > 0) {
								double currentWorth = hostiles[0].health * 1.2 + location.distanceSquaredTo(hostiles[0].location);
								for (int i = 1; i < hostiles.length; i++) {
									double worth = hostiles[i].health + location.distanceSquaredTo(hostiles[i].location);
									if (worth < currentWorth) {
										botIndex = i;
										currentWorth = worth;
									}
								}
								int distanceTo = location.distanceSquaredTo(hostiles[botIndex].location);
								if (rc.canAttackLocation(hostiles[botIndex].location))
									rc.attackLocation(hostiles[botIndex].location);
								else if (rc.isCoreReady())
									if (botType.attackRadiusSquared - distanceTo < 0 && distanceTo < MAX_COMBAT_RANGE) {
										pathTo(hostiles[botIndex].location);
									}
									else
										pathTo(spawnPoint);
							}
							else if (rc.isCoreReady())
								pathTo(spawnPoint);
						}
					} catch (Exception e) {
						System.out.println(e.getMessage());
						e.printStackTrace();
					}
					Clock.yield();
				}
			} else if (botType == robotTypes[6]) { // scout
				archonSpawns = rc.getInitialArchonLocations(teams[0]);
				while (true) {
					try {
						switch (flagScout) {
						case 0: {
							boolean hasBeenScouted = false;
							for (int i = scoutedLocationsIndex - 1; i >= 0; i--) {
								if (location.equals(scoutedLocations[i])) {
									hasBeenScouted = true;
									break;
								}
							}
							if (!hasBeenScouted) {
								for (int i = 1; edgeOffset[0] == 1000 && i * i < botType.sensorRadiusSquared / 2; i++) {
									if (rc.canSense(location.add(directions[0], i)) && !rc.onTheMap(location.add(directions[0], i)))
										edgeOffset[0] = i;
								}
								for (int i = 1; edgeOffset[1] == 1000 && i * i < botType.sensorRadiusSquared / 2; i++) {
									if (rc.canSense(location.add(directions[2], i)) && !rc.onTheMap(location.add(directions[2], i)))
										edgeOffset[1] = i;
								}
								for (int i = 1; edgeOffset[2] == 1000 && i * i < botType.sensorRadiusSquared / 2; i++) {
									if (rc.canSense(location.add(directions[4], i)) && !rc.onTheMap(location.add(directions[4], i)))
										edgeOffset[2] = i;
								}
								for (int i = 1; edgeOffset[3] == 1000 && i * i < botType.sensorRadiusSquared / 2; i++) {
									if (rc.canSense(location.add(directions[6], i)) && !rc.onTheMap(location.add(directions[6], i)))
										edgeOffset[3] = i;
								}
								scoutedLocations[scoutedLocationsIndex] = location;
								scoutedLocationsIndex++;
							}
							if (edgeOffset[0] + edgeOffset[1] + edgeOffset[2] + edgeOffset[3] < 2500) {
								int sig1 = (edgeOffset[1] != 1000 ? edgeOffset[1] : edgeOffset[3]);
								int sig2 = (edgeOffset[0] != 1000 ? edgeOffset[0] : edgeOffset[0]);
								corner = new MapLocation(location.x + sig1, location.y + sig2);
							}
							if (corner != null && location.distanceSquaredTo(spawnPoint) < 36) {
								rc.broadcastMessageSignal(corner.x, corner.y, 64);
								flagScout++;
							}
							if (corner != null || rc.senseHostileRobots(location, botType.sensorRadiusSquared).length - 4 > 0 || rc.getHealth() * 2 - botType.maxHealth < 0) {
								if (rc.isCoreReady()) {
									if (pathTo(spawnPoint) && rc.getHealth() * 2 - botType.maxHealth < 0)
										rc.broadcastSignal(4);
									if (corner == null) {
										if (edgeOffset[0] != 1000) edgeOffset[0] += location.y - location.y;
										if (edgeOffset[1] != 1000) edgeOffset[1] += location.x - location.x;
										if (edgeOffset[2] != 1000) edgeOffset[2] += location.y - location.y;
										if (edgeOffset[3] != 1000) edgeOffset[3] += location.x - location.x;
									}
								}
							}
							else {
								if (rc.isCoreReady()) {
									MapLocation nextLocation;
									nextLocation = location.add(directions[(location.directionTo(scoutedLocations[scoutedLocationsIndex - 1]).ordinal() % 8)], 2);
									pathTo(nextLocation);
									if (corner == null) {
										if (edgeOffset[0] != 1000) edgeOffset[0] += location.y - location.y;
										if (edgeOffset[1] != 1000) edgeOffset[1] += location.x - location.x;
										if (edgeOffset[2] != 1000) edgeOffset[2] += location.y - location.y;
										if (edgeOffset[3] != 1000) edgeOffset[3] += location.x - location.x;
									}
								}
							}
							break;
						}
						case 1: {

							if (rc.isCoreReady()) {
								if (archonsNotified < archonSpawns.length) {
									int closestArchon = -1;
									for (int i = 0; i < archonSpawns.length; i++) {
										if (location.distanceSquaredTo(archonSpawns[i]) < closestArchon)
											closestArchon = i;
									}
									if (closestArchon >= 0)
										if (location.distanceSquaredTo(archonSpawns[closestArchon]) <= 36) {
											rc.broadcastMessageSignal(corner.x, corner.y, 64);
											archonsNotified++;
										}
										else if (rc.isCoreReady())
											pathTo(archonSpawns[closestArchon]);
								}
								else {
									flagScout++;
								}
							}

							break;
						}
						default: {

							pathTo(location.add(directions[(int)(Math.random() * 8)]));
							break;
						}
						}

					} catch (Exception e) {
						System.out.println(e.getMessage());
						e.printStackTrace();
					}
					Clock.yield();
				}
			} else { // other types of bots go here
				while (true) {
					Clock.yield();
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public static boolean pathTo(MapLocation destination) throws Exception {
		direction = location.directionTo(destination);
		distanceToGoal = location.distanceSquaredTo(destination);
		candidates[0] = direction.ordinal();
		candidates[1] = direction.rotateLeft().ordinal();
		candidates[2] = direction.rotateRight().ordinal();
		if (location.equals(destination) || location.isAdjacentTo(destination)) {
			direction = Direction.NONE;
			return true;
		}
		else {
			// where the actual pathing takes place
			int startDirsIndex = lastDirsIndex;
			for (int i = 0; i < candidates.length && startDirsIndex == lastDirsIndex; i++) { // look for the three directions closest to the target
				if (lastDirs[(lastDirsIndex - 1 < 0 ? 0 : lastDirsIndex - 1)] != (i + 4) % 8 && lastDirs[(lastDirsIndex - 2 < 0 ? 0 : lastDirsIndex - 2)] != (i + 4) % 8) // if the previous two directions weren't involved
					if (rc.senseRubble(location.add(directions[candidates[i]])) < 50 || botType.ignoresRubble) { // if there is no rubble whatsoever
						if (rc.canMove(directions[candidates[i]])) { // if the direction works
							rc.move(directions[candidates[i]]); // go as forward as possible
							location = rc.getLocation(); // mark the new location
							lastDirs[lastDirsIndex] = candidates[i]; // mark the direction traveled down in the log
							if (lastDirsIndex - lastDirs.length + 1 < 0) // relocate the index marker to accommodate the newly logged direction
								lastDirsIndex++;
							else
								lastDirsIndex = 0;
							return false;
						}
					}
			}
			if (startDirsIndex == lastDirsIndex) { // if no plausible direction was found free of rubble
				for (int i = 0; i < candidates.length && startDirsIndex == lastDirsIndex; i++) { // look for the three directions closest to the target
					if (lastDirs[(lastDirsIndex - 1 < 0 ? 0 : lastDirsIndex - 1)] != (i + 4) % 8 && lastDirs[(lastDirsIndex - 2 < 0 ? 0 : lastDirsIndex - 2)] != (i + 4) % 8) // if the previous two directions weren't involved
						if (rc.canMove(directions[candidates[i]])) { // if the direction works
							rc.move(directions[candidates[i]]); // go as forward as possible
							lastDirs[lastDirsIndex] = candidates[i]; // mark the direction traveled down in the log
							if (lastDirsIndex - lastDirs.length + 1 < 0) // relocate the index marker to accommodate the newly logged direction
								lastDirsIndex++;
							else
								lastDirsIndex = 0;
							return false;
						}
				}
				if (startDirsIndex == lastDirsIndex) { // if no plausible direction was found even with minor rubble
					// begin bugging

					for (int i = candidates[0]; i < candidates[0] + 8; i++) { // hug the wall in a clockwise manner
						if (lastDirs[(lastDirsIndex - 1 < 0 ? 0 : lastDirsIndex - 1)] != (i + 4) % 8 && lastDirs[(lastDirsIndex - 2 < 0 ? 0 : lastDirsIndex - 2)] != (i + 4) % 8) // if the previous two directions weren't involved
							if (rc.canMove(directions[i % 8])) {
								rc.move(directions[i % 8]); // move to the next direction
								lastDirs[lastDirsIndex] = directions[i % 8].ordinal(); // mark the direction traveled down in the log
								if (lastDirsIndex - lastDirs.length + 1 < 0) // relocate the index marker to accommodate the newly logged direction
									lastDirsIndex++;
								else
									lastDirsIndex = 0;
								return false;
							}
					}
					if (startDirsIndex == lastDirsIndex) { // if no plausible direction was found at all
						if (lastDirsIndex > 0){
							if (rc.canMove(directions[lastDirs[lastDirsIndex - 1]])) {
								rc.move(directions[lastDirs[lastDirsIndex - 1]]);
								lastDirsIndex--;
							}
						}
						else { // if moving is completely impossible
							for (int i = candidates[0]; i < candidates[0] + 8; i++) { // detect for ways to break free, such as neutrals or rubble
								if (rc.senseRubble(location.add(directions[i % 8])) >= 100) {
									rc.clearRubble(directions[i % 8]); // clear the discovered rubble
									return false;
								}
							}
						}
					}

				}
			}
		}
		return false;
	}

	public static boolean rushBuild(RobotType robot) throws Exception {
		int i = 0;
		while (!rc.canBuild(directions[i], robot) && i - 8 <= 0) //check all directions for bot building starting with N
			i++;
		if (i < 8) {
			rc.build(directions[i], robot); //build a bot in the closest free direction
			return true;
		}
		return false;
	}

	public static boolean isCorner(MapLocation ml) throws Exception {
		int ops = 0;
		if (rc.onTheMap(ml.add(directions[0]))) ops++;
		if (rc.onTheMap(ml.add(directions[2]))) ops++;
		if (rc.onTheMap(ml.add(directions[4]))) ops++;
		if (rc.onTheMap(ml.add(directions[6]))) ops++;
		if (ops == 2)
			return true;
		return false;
	}
}