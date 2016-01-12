package team061;

import java.util.Random;

import battlecode.common.*;

public class BotSoldier extends Bot {
	static MapLocation archonLoc;
	static MapLocation targetLoc;
	static int archonID;

	public static void loop(RobotController theRC) throws GameActionException {
		Bot.init(theRC);
		init();
		// Debug.init("micro");
		while (true) {
			try {
				turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}

	private static void init() throws GameActionException {
		// atScoutLocation = false;
		Signal[] signals = rc.emptySignalQueue();
		for (int i = 0; i < signals.length; i++) {
			int[] message = signals[i].getMessage();
			MessageEncode msgType = MessageEncode.whichStruct(message[0]);
			if (signals[i].getTeam() == us && msgType == MessageEncode.MOBILE_ARCHON_LOCATION) {
				int[] decodedMessage = MessageEncode.MOBILE_ARCHON_LOCATION.decode(signals[i].getLocation(), message);
				targetLoc = new MapLocation(decodedMessage[0], decodedMessage[1]);
				archonID = signals[i].getID();
				rc.setIndicatorString(0, "got archon loc");
				break;
			}
		}
	}

	private static void turn() throws GameActionException {
		int acceptableRangeSquared = RobotType.SOLDIER.attackRadiusSquared;
		// Check where moving Archon is
		here = rc.getLocation();
		// Check for nearby enemies
		RobotInfo[] enemies = rc.senseHostileRobots(here, RobotType.SOLDIER.sensorRadiusSquared);
		RobotInfo[] enemiesICanShoot = rc.senseHostileRobots(here, RobotType.SOLDIER.attackRadiusSquared);
		boolean targetUpdated = updateTargetLoc();
		rc.setIndicatorString(0, "target = " + targetLoc);
		// Closest Bad Guy
		RobotInfo closestEnemy = Util.closest(enemies, here);
		// Nav
		NavSafetyPolicy theSafety = new SafetyPolicyAvoidAllUnits(enemies);
		// If within acceptable range of target
		if (here.distanceSquaredTo(targetLoc) < acceptableRangeSquared) {
			// check we are within range
			int numEnemiesAttackingUs = 0;
			RobotInfo[] enemiesAttackingUs = new RobotInfo[99];
			for (RobotInfo enemy : enemies) {
				if (enemy.type.attackRadiusSquared >= here.distanceSquaredTo(enemy.location)) {
					enemiesAttackingUs[numEnemiesAttackingUs++] = enemy;
				}
			}

			// -- if we are getting owned and have core delay and can retreat
			if (enemies.length > 0) {
				rc.setIndicatorString(1, "kill");
				// without endangering archon, do so
				if (archonLoc != null)
					if (rc.isCoreReady() && (numEnemiesAttackingUs > 0 && (!nearEnemies(enemies, archonLoc))
							|| Util.closest(enemies, archonLoc).type != RobotType.ZOMBIEDEN)) {
						Combat.retreat(Util.closest(enemies, targetLoc).location);
					}
				// --otherwise hit an enemy if we can
				if (rc.isWeaponReady() && enemiesICanShoot.length > 0) {
					Combat.shootAtNearbyEnemies();
				}

				// -if we are not getting hit:
				if (numEnemiesAttackingUs < 1) {
					// -- if we can assist an ally who is engaged, do so
					int numAlliesFightingEnemy = numOtherAlliesInAttackRange(closestEnemy.location);
					int maxEnemyExposure = numAlliesFightingEnemy;
					tryMoveTowardLocationWithMaxEnemyExposure(closestEnemy.location, maxEnemyExposure, enemies);
				}
				if (archonLoc != null)
					if (nearEnemies(enemies, archonLoc)) {
						moveInFrontOfTheArchon(Util.closest(enemies, targetLoc));
					}
			}
			else if (isOpposite(archonLoc,targetLoc) && rc.isCoreReady()) {
				Nav.goTo(here.add(here.directionTo(archonLoc).rotateLeft()), theSafety);
			}
			// -- if we left no room around the archon, give him some space
			//if (isAboutOpposite(archonLoc,targetLoc) && rc.isCoreReady()) {
				//Nav.goTo(here.add(here.directionTo(archonLoc).rotateLeft()), theSafety);
			//}
			// -- if there is an enemy harasser nearby,protect archon

		} else {
			rc.setIndicatorString(1, "nothing to kill on round " + rc.getRoundNum());

			// -if near enemy try to attack anything near us
			if (rc.isWeaponReady() && enemiesICanShoot.length > 0) {
				rc.setIndicatorString(0, "shooting enemy on round " + rc.getRoundNum());
				Combat.shootAtNearbyEnemies();
			}
			// -else begin searching for target
			else if (rc.isCoreReady() && here != targetLoc) {
				rc.setIndicatorString(2, "trying to go to target on round " + rc.getRoundNum());
				Nav.goTo(targetLoc, theSafety);
			}
		}

	}

	private static boolean updateTargetLoc() {
		Signal[] signals = rc.emptySignalQueue();
		for (Signal signal : signals) {
			if (signal.getTeam() == us && signal.getID() == archonID) {
				rc.setIndicatorString(1, "updating from message");
				int[] message = signal.getMessage();
				if (message != null) {
					MapLocation senderloc = signal.getLocation();
					MessageEncode purpose = MessageEncode.whichStruct(message[0]);
					if (purpose == MessageEncode.DIRECT_MOBILE_ARCHON) {
						int[] data = purpose.decode(senderloc, message);
						targetLoc = new MapLocation(data[0], data[1]);
						return true;
					}
					if (purpose == MessageEncode.MOBILE_ARCHON_LOCATION) {
						int[] data = purpose.decode(senderloc, message);
						archonLoc = new MapLocation(data[0], data[1]);
						return true;
					}
				}
			}
		}
		/*
		 * RobotInfo[] allies =
		 * rc.senseNearbyRobots(RobotType.SOLDIER.sensorRadiusSquared, us);
		 * for(RobotInfo ally : allies){ if(ally.ID == archonID){ targetLoc =
		 * ally.location; return true; } }
		 */
		return false;
	}
	/*
	 * private static MapLocation checkScouttargetLoc(Signal[] signals) {
	 * MapLocation targetLoc; for (int i = 0; i < signals.length; i++) { if
	 * (signals[i].getTeam() == them) { continue; } int[] message =
	 * signals[i].getMessage(); MessageEncode msgType =
	 * MessageEncode.whichStruct(message[0]); if (signals[i].getTeam() == us &&
	 * msgType == MessageEncode.MOBILE_ARCHON_LOCATION) { int[] decodedMessage =
	 * MessageEncode.MOBILE_ARCHON_LOCATION.decode(message); targetLoc = new
	 * MapLocation(decodedMessage[0], decodedMessage[1]); return targetLoc; } }
	 * // }
	 */

	private static void moveInFrontOfTheArchon(RobotInfo closestEnemy) {
		// RobotInfo closestEnemy = Util.closest(enemies, here);
		Direction directionToEnemyFromArchon = targetLoc.directionTo(closestEnemy.location);
		MapLocation goToHere = targetLoc.add(directionToEnemyFromArchon);
		RobotInfo[] stuff = {};
		NavSafetyPolicy theSafety = new SafetyPolicyAvoidAllUnits(stuff);
		try {
			if (rc.isCoreReady())
				Nav.goTo(goToHere, theSafety);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static boolean nearEnemies(RobotInfo[] enemies, MapLocation here) {
		RobotInfo closestEnemy = Util.closest(enemies, here);
		if (closestEnemy != null
				&& here.distanceSquaredTo(closestEnemy.location) < closestEnemy.type.attackRadiusSquared)
			return true;
		return false;
	}

	private static int numOtherAlliesInAttackRange(MapLocation loc) {
		int ret = 0;
		RobotInfo[] allies = rc.senseNearbyRobots(loc, 15, us);
		for (RobotInfo ally : allies) {
			if (ally.type.attackRadiusSquared >= loc.distanceSquaredTo(ally.location))
				ret++;
		}
		return ret;
	}

	private static boolean couldMoveOut(RobotInfo[] enemies, MapLocation here) {
		RobotInfo closestEnemy = Util.closest(enemies, here);
		int range = closestEnemy.type.attackRadiusSquared - here.distanceSquaredTo(closestEnemy.location);
		if (range > -1)
			return true;
		return false;
	}

	private static boolean tryMoveTowardLocationWithMaxEnemyExposure(MapLocation loc, int maxEnemyExposure,
			RobotInfo[] nearbyEnemies) throws GameActionException {
		Direction toLoc = here.directionTo(loc);
		Direction[] tryDirs = new Direction[] { toLoc, toLoc.rotateLeft(), toLoc.rotateRight() };
		for (Direction dir : tryDirs) {
			if (!rc.canMove(dir))
				continue;
			MapLocation moveLoc = here.add(dir);
			int enemyExposure = numEnemiesAttackingLocation(moveLoc, nearbyEnemies);
			if (enemyExposure <= maxEnemyExposure && rc.isCoreReady() && rc.canMove(dir)) {
				rc.move(dir);
				return true;
			}
		}

		return false;
	}

	private static boolean isArchonSurrounded() throws GameActionException {
		Direction dir = Direction.NORTH;
		Boolean surrounded = true;
		for (int i = 0; i < 8; i++) {
			MapLocation newLoc = targetLoc.add(dir);
			if (rc.onTheMap(newLoc) && !rc.isLocationOccupied(newLoc)) {
				surrounded = false;
				break;
			}
			dir = dir.rotateLeft();
		}
		return surrounded;
	}

	private static Direction openDirection() throws GameActionException {
		Direction dir = Direction.NORTH;
		Boolean surrounded = true;
		for (int i = 0; i < 8; i++) {
			MapLocation newLoc = targetLoc.add(dir);
			if (rc.onTheMap(newLoc) && !rc.isLocationOccupied(newLoc)) {
				break;
			}
			dir = dir.rotateLeft();
		}
		return dir;
	}

	private static int a;
	private static boolean isOpposite(MapLocation loc1, MapLocation loc2) {
		if (here.directionTo(loc1) == loc2.directionTo(here))
			return true;
	/*
		Direction[] dirs = { Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
				Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST };
		for (int x = 0; x < dirs.length; x++) {
			if (dirs[x] == here.directionTo(loc1))
				;
			a = x;
		}
		if (loc2.directionTo(here) == dirs[(a + 1)%dirs.length] || loc2.directionTo(here) == dirs[a - 1])
			return true;*/
		return false;
	}

	private static int numEnemiesAttackingLocation(MapLocation loc, RobotInfo[] nearbyEnemies) {
		int ret = 0;
		for (int i = nearbyEnemies.length; i-- > 0;) {
			if (nearbyEnemies[i].type.attackRadiusSquared >= loc.distanceSquaredTo(nearbyEnemies[i].location))
				ret++;
		}
		return ret;
	}

}