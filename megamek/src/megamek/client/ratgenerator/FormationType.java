/**
 * 
 */
package megamek.client.ratgenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import megamek.common.AmmoType;
import megamek.common.Compute;
import megamek.common.EntityMovementMode;
import megamek.common.EntityWeightClass;
import megamek.common.EquipmentMode;
import megamek.common.EquipmentType;
import megamek.common.MechSummary;
import megamek.common.MiscType;
import megamek.common.UnitType;
import megamek.common.WeaponType;
import megamek.common.weapons.ACWeapon;
import megamek.common.weapons.ArtilleryWeapon;
import megamek.common.weapons.LBXACWeapon;
import megamek.common.weapons.LRMWeapon;
import megamek.common.weapons.SRMWeapon;
import megamek.common.weapons.TAGWeapon;
import megamek.common.weapons.UACWeapon;

/**
 * Campaign Operations rules for force generation.
 * 
 * @author Neoancient
 *
 */
public class FormationType {
    
    public enum UnitRole {
        UNDETERMINED (false),
        AMBUSHER (true),
        BRAWLER (true),
        JUGGERNAUT (true),
        MISSILE_BOAT (true),
        SCOUT (true),
        SKIRMISHER (true),
        SNIPER (true),
        STRIKER (true),
        ATTACK_FIGHTER (false),
        DOGFIGHTER (false),
        FAST_DOGFIGHTER (false),
        FIRE_SUPPORT (false),
        INTERCEPTOR (false),
        TRANSPORT (false);
        
        private boolean ground;
        
        UnitRole(boolean ground) {
            this.ground = ground;
        }
        
        public boolean isGroundRole() {
            return ground;
        }
        
        public static UnitRole parseRole(String role) {
            switch (role.toLowerCase()) {
            case "ambusher":
                return AMBUSHER;
            case "brawler":
                return BRAWLER;
            case "juggernaut":
                return JUGGERNAUT;
            case "missile_boat":
            case "missile boat":
                return MISSILE_BOAT;
            case "scout":
                return SCOUT;
            case "skirmisher":
                return SKIRMISHER;
            case "sniper":
                return SNIPER;
            case "striker":
                return STRIKER;
            case "attack_fighter":
            case "attack figher":
            case "attack":
                return ATTACK_FIGHTER;
            case "dogfighter":
                return DOGFIGHTER;
            case "fast_dogfighter":
            case "fast dogfighter":
                return FAST_DOGFIGHTER;
            case "fire_support":
            case "fire support":
            case "fire-support":
                return FIRE_SUPPORT;
            case "interceptor":
                return INTERCEPTOR;
            case "transport":
                return TRANSPORT;
            default:
                System.err.println("Could not parse AS Role " + role);
                return UNDETERMINED;
            }
        }        
    };
    
    public final static int FLAG_MEK = 1 << UnitType.MEK; 
    public final static int FLAG_TANK = 1 << UnitType.TANK; 
    public final static int FLAG_BATTLE_ARMOR = 1 << UnitType.BATTLE_ARMOR; 
    public final static int FLAG_INFANTRY = 1 << UnitType.INFANTRY; 
    public final static int FLAG_PROTOMEK = 1 << UnitType.PROTOMEK; 
    public final static int FLAG_VTOL = 1 << UnitType.VTOL;
    public final static int FLAG_NAVAL = 1 << UnitType.NAVAL;
    
    public final static int FLAG_CONV_FIGHTER = 1 << UnitType.CONV_FIGHTER; 
    public final static int FLAG_AERO = 1 << UnitType.AERO; 
    public final static int FLAG_SMALL_CRAFT = 1 << UnitType.SMALL_CRAFT; 
    public final static int FLAG_DROPSHIP = 1 << UnitType.DROPSHIP;

    public final static int FLAG_GROUND = FLAG_MEK | FLAG_TANK | FLAG_BATTLE_ARMOR | FLAG_INFANTRY
            | FLAG_PROTOMEK | FLAG_VTOL | FLAG_NAVAL;
    public final static int FLAG_GROUND_NO_LIGHT = FLAG_MEK | FLAG_TANK | FLAG_BATTLE_ARMOR
            | FLAG_PROTOMEK | FLAG_NAVAL;
    public final static int FLAG_FIGHTER = FLAG_CONV_FIGHTER | FLAG_AERO;
    public final static int FLAG_AIR = FLAG_CONV_FIGHTER | FLAG_AERO | FLAG_SMALL_CRAFT
            | FLAG_DROPSHIP;
    public final static int FLAG_VEHICLE = FLAG_TANK | FLAG_NAVAL | FLAG_VTOL;
    public final static int FLAG_ALL = FLAG_GROUND | FLAG_AIR;
    
    private static HashMap<String,FormationType> allFormationTypes = null;
    public static FormationType getFormationType(String key) {
        if (allFormationTypes == null) {
            createFormationTypes();
        }
        return allFormationTypes.get(key);
    }
    
    public static Collection<FormationType> getAllFormations() {
        if (allFormationTypes == null) {
            createFormationTypes();
        }
        return allFormationTypes.values();
    }
    
    protected FormationType(String name) {
        this(name, name);
    }
    
    protected FormationType(String name, String category) {
        this.name = name;
        this.category = category;
    }
    
    private String name = "Support";
    private String category = null;
    private int allowedUnitTypes = FLAG_GROUND;
    // Some formation types allow units not normally generated for general combat roles (e.g. artillery, cargo)  
    private EnumSet<MissionRole> missionRoles = EnumSet.noneOf(MissionRole.class);
    // If all units in the force have this role, other constraints can be ignored.
    private UnitRole idealRole = UnitRole.UNDETERMINED;
    
    private int minWeightClass = 0;
    private int maxWeightClass = EntityWeightClass.SIZE;
    // Used as a filter when generating units
    private Predicate<MechSummary> mainCriteria = ms -> true;
    // Additional criteria that have to be fulfilled by a portion of the force
    private List<Constraint> otherCriteria = new ArrayList<>();
    private GroupingConstraint groupingCriteria = null;
    
    public String getName() {
        return name;
    }
    
    public String getCategory() {
        return category;
    }
    
    public int getAllowedUnitTypes() {
        return allowedUnitTypes;
    }
    
    public boolean isGround() {
        return (allowedUnitTypes & FLAG_AERO) == 0;
    }
    
    public UnitRole getIdealRole() {
        return idealRole;
    }

    public int getMinWeightClass() {
        return minWeightClass;
    }

    public int getMaxWeightClass() {
        return maxWeightClass;
    }
    
    private static UnitRole getUnitRole(MechSummary ms) {
        ModelRecord mRec = RATGenerator.getInstance().getModelRecord(ms.getName());
        return mRec == null? UnitRole.UNDETERMINED : mRec.getUnitRole();
    }
    
    private static Set<MissionRole> getMissionRoles(MechSummary ms) {
        ModelRecord mRec = RATGenerator.getInstance().getModelRecord(ms.getName());
        return mRec == null? EnumSet.noneOf(MissionRole.class) : mRec.getRoles();
    }
    
    private static int getDamageAtRange(MechSummary ms, int range) {
        int retVal = 0;
        for (int i = 0; i < ms.getEquipmentNames().size(); i++) {
            if (EquipmentType.get(ms.getEquipmentNames().get(i)) instanceof WeaponType) {
                final WeaponType weapon = (WeaponType)EquipmentType.get(ms.getEquipmentNames().get(i));
                if (weapon.getLongRange() < range) {
                    continue;
                }
                int damage = 0;
                if (weapon.getAmmoType() != AmmoType.T_NA) {
                    Optional<EquipmentType> ammo = ms.getEquipmentNames().stream()
                        .map(name -> EquipmentType.get(name))
                        .filter(eq -> eq instanceof AmmoType
                                && ((AmmoType)eq).getAmmoType() == weapon.getAmmoType()
                                && ((AmmoType)eq).getRackSize() == weapon.getRackSize())
                        .findFirst();
                    if (ammo.isPresent()) {
                        damage = ((AmmoType)ammo.get()).getDamagePerShot()
                                * ((AmmoType)ammo.get()).getRackSize();
                    }
                } else {
                    damage = weapon.getDamage(range);
                }
                if (damage > 0) {
                    retVal += damage * ms.getEquipmentQuantities().get(i);
                }
            }
        }
        return retVal;
    }
    
    public List<MechSummary> generateFormation(UnitTable.Parameters params, int numUnits,
            boolean bestEffort) {
        List<UnitTable.Parameters> p = new ArrayList<>();
        p.add(params);
        List<Integer> n = new ArrayList<>();
        n.add(numUnits);
        return generateFormation(p, n, bestEffort);
    }
    
    public List<MechSummary> generateFormation(List<UnitTable.Parameters> params, List<Integer> numUnits,
            boolean bestEffort) {
        if (params.size() != numUnits.size()) {
            throw new IllegalArgumentException("Formation parameter list and numUnit list must have the same number of elements.");
        }
        
        List<Integer> wcs = IntStream.range(minWeightClass,
                Math.min(maxWeightClass, EntityWeightClass.WEIGHT_SUPER_HEAVY))
                .mapToObj(Integer::valueOf)
                .collect(Collectors.toList());
        List<Integer> airWcs = wcs.stream().filter(wc -> wc < EntityWeightClass.WEIGHT_ASSAULT)
                .collect(Collectors.toList()); 
        params.forEach(p -> {
            p.getRoles().addAll(missionRoles);
            p.setWeightClasses(p.getUnitType() < UnitType.CONV_FIGHTER ? wcs : airWcs);
        });
        List<UnitTable> tables = params.stream().map(UnitTable::findTable).collect(Collectors.toList());
        //If there are any parameter sets that cannot generate a table, return an empty list. 
        if (!tables.stream().allMatch(UnitTable::hasUnits) && !bestEffort) {
            return new ArrayList<>();
        }
        
        /* Check whether we have vees that do not have the movement mode(s) set. If so,
         * we will attempt to conform them to a single type. Any that are set are ignored;
         * there is no attempt to conform to mode already in the force. If they are intended
         * to conform, they ought to be set.
         */
        List<Integer> undeterminedVees = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).getUnitType() == UnitType.TANK
                    && params.get(i).getMovementModes().isEmpty()) {
                undeterminedVees.add(i);
            }
        }
        if (undeterminedVees.size() > 0) {
            /* Look at the table for each group of parameters and determine the motive type
             * ratio, then weight those values according to the number of units using those
             * parameters.
             */
            Map<String,Integer> mmMap = new HashMap<>();
            for (int i = 0; i < undeterminedVees.size(); i++) {
                for (int j = 0; j < tables.get(i).getNumEntries(); j++) {
                    if (tables.get(i).getMechSummary(j) != null) {
                        mmMap.merge(tables.get(i).getMechSummary(j).getUnitSubType(),
                                tables.get(i).getEntryWeight(j) * numUnits.get(i), Integer::sum);
                    }
                }
            }
            while (!mmMap.isEmpty()) {
                int total = mmMap.values().stream().mapToInt(Integer::intValue).sum();
                int r = Compute.randomInt(total);
                String mode = "Tracked";
                for (String m : mmMap.keySet()) {
                    if (r < mmMap.get(m)) {
                        mode = m;
                        break;
                    } else {
                        r -= mmMap.get(m);
                    }
                }
                mmMap.remove(mode);
                
                List<UnitTable.Parameters> tempParams = params.stream().map(UnitTable.Parameters::copy)
                        .collect(Collectors.toList());
                for (int index : undeterminedVees) {
                    tempParams.get(index).getMovementModes().add(EntityMovementMode.getMode(mode));
                }
                List<MechSummary> list = generateFormation(tempParams, numUnits, false);
                if (!list.isEmpty()) {
                    return list;
                }
            }
            /* If we cannot meet all criteria with a specific motive type, try without respect to motive type */
        }

        int cUnits = (int)numUnits.stream().mapToInt(Integer::intValue).sum();

        /* Simple case: all units have the same requirements. */
        if (otherCriteria.isEmpty() && groupingCriteria == null) {
            List<MechSummary> retVal = new ArrayList<>();
            for (int i = 0; i < params.size(); i++) {
                retVal.addAll(tables.get(i).generateUnits(numUnits.get(i),
                        ms -> mainCriteria.test(ms)));
            }
            if (retVal.size() < cUnits) {
                List<MechSummary> matchRole = tryIdealRole(params, numUnits);
                if (matchRole != null) {
                    return matchRole;
                }
            }
            return retVal;
        }
        
        /* Simple case: single set of parameters and single additional criterion. */
        if (params.size() == 1 && otherCriteria.size() == 1 && groupingCriteria == null) {
            List<MechSummary> retVal = new ArrayList<>();
            retVal.addAll(tables.get(0).generateUnits(otherCriteria.get(0).getMinimum(numUnits.get(0)),
                    ms -> mainCriteria.test(ms) && otherCriteria.get(0).criterion.test(ms)));
            if (retVal.size() < otherCriteria.get(0).getMinimum(numUnits.get(0))) {
                List<MechSummary> onRole = tryIdealRole(params, numUnits);
                if (onRole != null) {
                    return onRole;
                } else if (!bestEffort) {
                    return new ArrayList<>();
                }
            }
            if (retVal.size() >= otherCriteria.get(0).getMinimum(numUnits.get(0)) || bestEffort) {
                retVal.addAll(tables.get(0).generateUnits(numUnits.get(0) - retVal.size(),
                        ms -> mainCriteria.test(ms)));
            }
            return retVal;
        }
        
        /* General case:
         * For each constraint, find all permutations of the multiset [0,1] with cardinality
         * k = total formation size and multiplicity(1) = minimum number
         * of units to fulfill the constraint. This is stored as a k-bit number in which 1 indicates
         * the unit at that index must fit the criterion and a 0 indicates no requirement.
         * 
         * The total number of ways to meet all requirements is equal to the product of the number
         * of permutations of all constraints. We can randomly select a number then decode it to
         * get the actual bitmaps for each constraint. 
         */
        
        /* First group all possible values of k bits into lists keyed to the number of
         * bits that are set.
         */
        Map<Integer,List<Integer>> bitCountMask = IntStream.rangeClosed(0, 1 << cUnits)
                .mapToObj(Integer::valueOf)
                .collect(Collectors.groupingBy(Integer::bitCount));
        
        /* Calculate how many different possible combinations we can make and construct a list
         * of all values from 0 to total - 1. If a conforming unit cannot be constructed from
         * a chosen value, it can be removed from the list.
         */
        int totalCombos = otherCriteria.stream().map(c -> bitCountMask.get(c.getMinimum(cUnits)).size())
            .reduce(1, (a, b) -> a * b);
        List<Integer> possibilities = IntStream.range(0, totalCombos).mapToObj(Integer::valueOf)
                .collect(Collectors.toList());
        
        /* Prepare array that determines which UnitTable to use for each position in the formation */
        int[] unitTableIndex = new int[cUnits];
        int pos = 0;
        for (int i = 0; i < numUnits.size(); i++) {
            for (int j = 0; j < numUnits.get(i); j++) {
                unitTableIndex[pos] = i;
                pos++;
            }
        }
        
        /* Prepare an array for use with grouping constraints; a null at the unit's index indicates
         * it is not part of a group. Any other value gives its position within the group, which
         * can be used to find the first member of the group for comparison.
         */
        Integer[] gcIndex = new Integer[cUnits];
        if (groupingCriteria != null && groupingCriteria.appliesTo(params.get(0).getUnitType())) {
            int groupSize = Math.min(groupingCriteria.groupSize, numUnits.get(0));
            int numGroups = 0;
            if (groupSize > 0) {
                numGroups = Math.min(groupingCriteria.numGroups, numUnits.get(0) / groupSize);
            }
            for (int g = 0; g < numGroups; g++) {
                for (int i = 0; i < groupSize; i++) {
                    gcIndex[g * groupSize + i] = i;
                }
            }
        }
        
        List<MechSummary> retVal = new ArrayList<>();
        List<Predicate<MechSummary>> filter = new ArrayList<>();
        while (!possibilities.isEmpty()) {
            retVal.clear();
            int index = Compute.randomInt(possibilities.size());
            int value = possibilities.get(index);
            
            /* Decode the value and get the bitmask for each constraint */
            int[] bitmasks = new int[otherCriteria.size()];
            for (int i = 0; i < otherCriteria.size(); i++) {
                int min = otherCriteria.get(i).getMinimum(cUnits);
                int cBitmaps = bitCountMask.get(min).size();
                bitmasks[i] = bitCountMask.get(min).get(value % cBitmaps);
                value /= cBitmaps;
            }

            boolean completed = true;
            for (int i = 0; i < cUnits; i++) {
                filter.clear();
                filter.add(mainCriteria);
                for (int j = 0; j < otherCriteria.size(); j++) {
                    if ((bitmasks[j] & 1) != 0) {
                        filter.add(otherCriteria.get(j).criterion);
                    }
                }
                if (gcIndex[i] != null) {
                    filter.add(groupingCriteria.generalConstraint);
                    if (gcIndex[i] > 0) {
                        final MechSummary base = retVal.get(i - gcIndex[i]);
                        filter.add(ms -> groupingCriteria.groupConstraint.apply(base, ms));
                    }
                }
                MechSummary unit = tables.get(unitTableIndex[i])
                        .generateUnit(ms -> filter.stream().allMatch(f -> f.test(ms)));
                if (unit == null) {
                    completed = false;
                    break;
                } else {
                    retVal.add(unit);
                    for (int j = 0; j < bitmasks.length; j++) {
                        bitmasks[j] >>= 1;
                    }
                }
            }
            if (completed) {
                return retVal;
            } else {
                possibilities.remove(index);
            }
        }
        List<MechSummary> onRole = tryIdealRole(params, numUnits);
        if (onRole != null) {
            return onRole;
        }
        return new ArrayList<>();
    }
    
    /**
     * Attempts to build unit entirely on ideal role. Returns null if unsuccessful.
     */
    private List<MechSummary> tryIdealRole(List<UnitTable.Parameters> params, List<Integer> numUnits) {
        if (idealRole.equals(UnitRole.UNDETERMINED)) {
            return null;
        }
        List<UnitTable.Parameters> tmpParams = params.stream()
                .map(p -> p.copy()).collect(Collectors.toList());
        tmpParams.forEach(p -> p.getWeightClasses().clear());
        List<MechSummary> retVal = new ArrayList<>();
        for (int i = 0; i < tmpParams.size(); i++) {
            UnitTable t = UnitTable.findTable(tmpParams.get(i));
            List<MechSummary> units = t.generateUnits(numUnits.get(i),
                    ms -> getUnitRole(ms).equals(idealRole));
            if (units.size() < numUnits.get(i)) {
                return null;
            }
        }
        return retVal;
    }
    
    public static void createFormationTypes() {
        allFormationTypes = new HashMap<>();
        createAssaultLance();
        createAnvilLance();
        createFastAssaultLance();
        createHunterLance();
        createBattleLance();
        createLightBattleLance();
        createMediumBattleLance();
        createHeavyBattleLance();
        createRifleLance();
        createBerserkerLance();
        createCommandLance();
        createOrderLance();
        createVehicleCommandLance();
        createFireLance();
        createAntiAirLance();
        createArtilleryFireLance();
        createDirectFireLance();
        createFireSupportLance();
        createLightFireLance();
        createPursuitLance();
        createProbeLance();
        createSweepLance();
        createReconLance();
        createHeavyReconLance();
        createLightReconLance();
        createSecurityLance();
        createStrikerCavalryLance();
        createHammerLance();
        createHeavyStrikerCavalryLance();
        createHordeLance();
        createLightStrikerCavalryLance();
        createRangerLance();
        createUrbanLance();
        createAerospaceSuperioritySquadron();
        createEWSquadron();
        createFireSupportSquadron();
        createInterceptorSquadron();
        createStrikeSquadron();
        createTransportSquadron();
    }
    
    private static void createAssaultLance() {
        FormationType ft = new FormationType("Assault");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.idealRole = UnitRole.JUGGERNAUT;
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getTotalArmor() >= 135;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> getDamageAtRange(ms, 7) >= 25));
        ft.otherCriteria.add(new CountConstraint(3,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        //FIXME: The actual requirement is one juggernaut or two snipers; there needs to be
        // a way to combine constraints with ||.
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.JUGGERNAUT, UnitRole.SNIPER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createAnvilLance() {
        FormationType ft = new FormationType("Anvil", "Assault");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getTotalArmor() >= 40;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getEquipmentNames().stream().map(name -> EquipmentType.get(name))
                    .anyMatch(eq -> eq instanceof ACWeapon
                            || eq instanceof LBXACWeapon
                            || eq instanceof UACWeapon
                            || eq instanceof SRMWeapon
                            || eq instanceof LRMWeapon)));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createFastAssaultLance() {
        FormationType ft = new FormationType("Fast Assault", "Assault");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getTotalArmor() >= 135
                && (ms.getWalkMp() >= 5 || ms.getJumpMp() > 0);
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> getDamageAtRange(ms, 7) >= 25));
        ft.otherCriteria.add(new CountConstraint(3,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        //FIXME: The actual requirement is one juggernaut or two snipers; there needs to be
        // a way to combine constraints with ||.
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.JUGGERNAUT, UnitRole.SNIPER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createHunterLance() {
        FormationType ft = new FormationType("Hunter", "Assault");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.idealRole = UnitRole.AMBUSHER;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> EnumSet.of(UnitRole.JUGGERNAUT, UnitRole.AMBUSHER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
        
    private static void createBattleLance() {
        FormationType ft = new FormationType("Battle");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.idealRole = UnitRole.BRAWLER;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> EnumSet.of(UnitRole.BRAWLER, UnitRole.SNIPER, UnitRole.SKIRMISHER)
                    .contains(getUnitRole(ms))));
        ft.groupingCriteria = new GroupingConstraint(FLAG_VEHICLE, 2, 2,
                ms -> ms.getWeightClass() == EntityWeightClass.WEIGHT_HEAVY,
                (ms0, ms1) -> ms0.getName().equals(ms1.getName()));
        allFormationTypes.put(ft.name, ft);
    }

    private static void createLightBattleLance() {
        FormationType ft = new FormationType("Light Battle", "Battle");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> ms.getWeightClass() == EntityWeightClass.WEIGHT_LIGHT));
        ft.groupingCriteria = new GroupingConstraint(FLAG_VEHICLE, 2, 2,
                ms -> ms.getWeightClass() == EntityWeightClass.WEIGHT_LIGHT,
                (ms0, ms1) -> ms0.getName().equals(ms1.getName()));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createMediumBattleLance() {
        FormationType ft = new FormationType("Medium Battle", "Battle");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWeightClass() == EntityWeightClass.WEIGHT_MEDIUM));
        ft.groupingCriteria = new GroupingConstraint(FLAG_VEHICLE, 2, 2,
                ms -> ms.getWeightClass() == EntityWeightClass.WEIGHT_MEDIUM,
                (ms0, ms1) -> ms0.getName().equals(ms1.getName()));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createHeavyBattleLance() {
        FormationType ft = new FormationType("Heavy Battle", "Battle");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        ft.groupingCriteria = new GroupingConstraint(FLAG_VEHICLE, 2, 2,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY,
                (ms0, ms1) -> ms0.getName().equals(ms1.getName()));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createRifleLance() {
        FormationType ft = new FormationType("Rifle", "Battle");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 4;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> ms.getWeightClass() <= EntityWeightClass.WEIGHT_HEAVY));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getEquipmentNames().stream().map(name -> EquipmentType.get(name))
                    .anyMatch(eq -> eq instanceof ACWeapon
                            || eq instanceof LBXACWeapon
                            || eq instanceof UACWeapon))); //UAC includes RAC
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createBerserkerLance() {
        FormationType ft = new FormationType("Berserker/Close", "Battle");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.idealRole = UnitRole.BRAWLER;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> EnumSet.of(UnitRole.BRAWLER, UnitRole.SNIPER, UnitRole.SKIRMISHER)
                    .contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createCommandLance() {
        FormationType ft = new FormationType("Command", "Command");
        ft.allowedUnitTypes = FLAG_MEK | FLAG_PROTOMEK;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> EnumSet.of(UnitRole.SNIPER, UnitRole.MISSILE_BOAT, UnitRole.SKIRMISHER,
                        UnitRole.JUGGERNAUT)
                    .contains(getUnitRole(ms))));
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> EnumSet.of(UnitRole.BRAWLER, UnitRole.STRIKER, UnitRole.SCOUT)
                    .contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createOrderLance() {
        FormationType ft = new FormationType("Order", "Command");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.groupingCriteria = new GroupingConstraint(FLAG_GROUND, Integer.MAX_VALUE, 1,
                ms -> true,
                (ms0, ms1) -> ms0.getChassis().equals(ms1.getChassis())
                    && ms0.getWeightClass() == ms1.getWeightClass());
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createVehicleCommandLance() {
        FormationType ft = new FormationType("Vehicle Command", "Command");
        ft.allowedUnitTypes = FLAG_TANK | FLAG_VTOL | FLAG_NAVAL;
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> EnumSet.of(UnitRole.BRAWLER, UnitRole.STRIKER, UnitRole.SCOUT)
                    .contains(getUnitRole(ms))));
        /* The description does not state how many pairs there need to be, but the reference to
         * "one of the pairs" implies there need to be at least two.
         */
        ft.groupingCriteria = new GroupingConstraint(FLAG_VEHICLE, 2, 2,
                ms -> EnumSet.of(UnitRole.SNIPER, UnitRole.MISSILE_BOAT, UnitRole.SKIRMISHER,
                        UnitRole.JUGGERNAUT)
                    .contains(getUnitRole(ms)),
                    (ms0, ms1) -> ms0.getName().equals(ms1.getName()));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createFireLance() {
        FormationType ft = new FormationType("Fire");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.idealRole = UnitRole.MISSILE_BOAT;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> EnumSet.of(UnitRole.SNIPER, UnitRole.MISSILE_BOAT).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createAntiAirLance() {
        FormationType ft = new FormationType("Anti-Air", "Fire");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> EnumSet.of(UnitRole.SNIPER, UnitRole.MISSILE_BOAT).contains(getUnitRole(ms))));
        ft.otherCriteria.add(new CountConstraint(2,
                // should indicate it has anti-aircraft targeting quirk without having to load all entities
                ms -> getMissionRoles(ms).contains(MissionRole.ANTI_AIRCRAFT) 
                || ms.getEquipmentNames().stream().map(name -> EquipmentType.get(name))
                    .anyMatch(eq -> eq instanceof ACWeapon
                            || eq instanceof LBXACWeapon
                            || eq instanceof ArtilleryWeapon)));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createArtilleryFireLance() {
        FormationType ft = new FormationType("Artillery Fire", "Fire");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.missionRoles.add(MissionRole.MIXED_ARTILLERY);
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> ms.getEquipmentNames().stream().map(name -> EquipmentType.get(name))
                    .anyMatch(eq -> eq instanceof ArtilleryWeapon)));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createDirectFireLance() {
        FormationType ft = new FormationType("Direct Fire", "Fire");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.mainCriteria = ms -> getDamageAtRange(ms, 18) >= 10;
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createFireSupportLance() {
        FormationType ft = new FormationType("Fire Support", "Fire");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.otherCriteria.add(new CountConstraint(3, ms -> ms.getEquipmentNames().stream()
                .map(name -> EquipmentType.get(name))
                .filter(eq -> eq instanceof WeaponType && eq.hasModes())
                .anyMatch(eq -> {
                    for (Enumeration<EquipmentMode> e = eq.getModes(); e.hasMoreElements();) {
                        if (e.nextElement().toString().equals("Indirect")) {
                            return true;
                        }
                    }
                    return false;
                })));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createLightFireLance() {
        FormationType ft = new FormationType("Light Fire", "Fire");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createPursuitLance() {
        FormationType ft = new FormationType("Pursuit");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> ms.getWalkMp() >= 6));
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> getDamageAtRange(ms, 15) >= 5));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createProbeLance() {
        FormationType ft = new FormationType("Probe", "Pursuit");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        ft.mainCriteria = ms -> getDamageAtRange(ms, 9) >= 10;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> ms.getWalkMp() >= 6));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createSweepLance() {
        FormationType ft = new FormationType("Sweep", "Pursuit");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5
                && getDamageAtRange(ms, 6) >= 10;
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createReconLance() {
        FormationType ft = new FormationType("Recon");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.idealRole = UnitRole.SCOUT;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5;        
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.SCOUT, UnitRole.STRIKER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }

    private static void createHeavyReconLance() {
        FormationType ft = new FormationType("Heavy Recon", "Recon");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 4;        
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> ms.getWalkMp() >= 5));
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> getUnitRole(ms).equals(UnitRole.SCOUT)));
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createLightReconLance() {
        FormationType ft = new FormationType("Light Recon", "Recon");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_LIGHT;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 6
                && getUnitRole(ms).equals(UnitRole.SCOUT);        
        allFormationTypes.put(ft.name, ft);        
    }
    
    private static void createSecurityLance() {
        FormationType ft = new FormationType("Security");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> EnumSet.of(UnitRole.SCOUT, UnitRole.STRIKER).contains(getUnitRole(ms))));
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> EnumSet.of(UnitRole.SNIPER, UnitRole.MISSILE_BOAT).contains(getUnitRole(ms))));
        ft.otherCriteria.add(new MaxCountConstraint(1,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_ASSAULT));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createStrikerCavalryLance() {
        FormationType ft = new FormationType("Striker/Cavalry");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.idealRole = UnitRole.STRIKER;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5 || ms.getJumpMp() >= 4;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> EnumSet.of(UnitRole.STRIKER, UnitRole.SKIRMISHER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createHammerLance() {
        FormationType ft = new FormationType("Hammer", "Striker/Cavalry");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.idealRole = UnitRole.STRIKER;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5;
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createHeavyStrikerCavalryLance() {
        FormationType ft = new FormationType("Heavy Striker/Cavalry", "Striker/Cavalry");
        ft.allowedUnitTypes = FLAG_GROUND_NO_LIGHT;
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 4;
        ft.otherCriteria.add(new CountConstraint(3,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.STRIKER, UnitRole.SKIRMISHER).contains(getUnitRole(ms))));
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> getDamageAtRange(ms, 18) >= 5));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createHordeLance() {
        FormationType ft = new FormationType("Horde", "Striker/Cavalry");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_LIGHT;
        ft.mainCriteria = ms -> getDamageAtRange(ms, 9) <= 10;
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createLightStrikerCavalryLance() {
        FormationType ft = new FormationType("Light Striker/Cavalry", "Striker/Cavalry");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5;
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> getDamageAtRange(ms, 18) >= 5));
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.STRIKER, UnitRole.SKIRMISHER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createRangerLance() {
        FormationType ft = new FormationType("Ranger", "Striker/Cavalry");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createUrbanLance() {
        FormationType ft = new FormationType("Urban");
        ft.allowedUnitTypes = FLAG_GROUND;
        ft.idealRole = UnitRole.AMBUSHER;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getJumpMp() > 0
                    || ms.getUnitType().equals(UnitType.getTypeName(UnitType.INFANTRY))
                    || ms.getUnitType().equals(UnitType.getTypeName(UnitType.BATTLE_ARMOR))));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWalkMp() <= 4));
        allFormationTypes.put(ft.name, ft);        
    }
    
    private static void createAerospaceSuperioritySquadron() {
        FormationType ft = new FormationType("Aerospace Superiority Squadron");
        ft.allowedUnitTypes = FLAG_FIGHTER;
        ft.otherCriteria.add(new PercentConstraint(0.51,
                ms -> EnumSet.of(UnitRole.INTERCEPTOR, UnitRole.FAST_DOGFIGHTER).contains(getUnitRole(ms))));
        ft.groupingCriteria = new GroupingConstraint(FLAG_FIGHTER, 2, Integer.MAX_VALUE,
                ms -> true,
                (ms0, ms1) -> ms0.getChassis().equals(ms1.getChassis()));
        allFormationTypes.put(ft.name, ft);
    }

    private static void createEWSquadron() {
        FormationType ft = new FormationType("Electronic Warfare Squadron");
        ft.allowedUnitTypes = FLAG_FIGHTER;
        ft.otherCriteria.add(new PercentConstraint(0.51,
                ms -> ms.getEquipmentNames().stream().map(en -> EquipmentType.get(en))
                .anyMatch(et -> et instanceof TAGWeapon ||  
                        (et instanceof MiscType &&
                            (((MiscType)et).hasFlag(MiscType.F_BAP)
                            || ((MiscType)et).hasFlag(MiscType.F_ECM))))));
        ft.groupingCriteria = new GroupingConstraint(FLAG_FIGHTER, 2, Integer.MAX_VALUE,
                ms -> true,
                (ms0, ms1) -> ms0.getChassis().equals(ms1.getChassis()));
        allFormationTypes.put(ft.name, ft);                
    }

    private static void createFireSupportSquadron() {
        FormationType ft = new FormationType("Fire Support Squadron");
        ft.allowedUnitTypes = FLAG_FIGHTER;
        ft.mainCriteria = ms -> EnumSet.of(UnitRole.FIRE_SUPPORT,
                UnitRole.DOGFIGHTER).contains(getUnitRole(ms));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> getUnitRole(ms).equals(UnitRole.FIRE_SUPPORT)));
        ft.groupingCriteria = new GroupingConstraint(FLAG_FIGHTER, 2, Integer.MAX_VALUE,
                ms -> true,
                (ms0, ms1) -> ms0.getChassis().equals(ms1.getChassis()));
        allFormationTypes.put(ft.name, ft);                
    }

    private static void createInterceptorSquadron() {
        FormationType ft = new FormationType("Interceptor Squadron");
        ft.allowedUnitTypes = FLAG_FIGHTER;
        ft.otherCriteria.add(new PercentConstraint(0.51,
                ms -> getUnitRole(ms).equals(UnitRole.INTERCEPTOR)));
        ft.groupingCriteria = new GroupingConstraint(FLAG_FIGHTER, 2, Integer.MAX_VALUE,
                ms -> true,
                (ms0, ms1) -> ms0.getChassis().equals(ms1.getChassis()));
        allFormationTypes.put(ft.name, ft);                
    }

    private static void createStrikeSquadron() {
        FormationType ft = new FormationType("Strike Squadron");
        ft.allowedUnitTypes = FLAG_FIGHTER;
        ft.otherCriteria.add(new PercentConstraint(0.51,
                ms -> EnumSet.of(UnitRole.ATTACK_FIGHTER,
                        UnitRole.DOGFIGHTER).contains(getUnitRole(ms))));
        ft.groupingCriteria = new GroupingConstraint(FLAG_FIGHTER, 2, Integer.MAX_VALUE,
                ms -> true,
                (ms0, ms1) -> ms0.getChassis().equals(ms1.getChassis()));
        allFormationTypes.put(ft.name, ft);                
    }

    private static void createTransportSquadron() {
        FormationType ft = new FormationType("Transport Squadron");
        ft.allowedUnitTypes = FLAG_FIGHTER | FLAG_SMALL_CRAFT | FLAG_DROPSHIP;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> getUnitRole(ms).equals(UnitRole.TRANSPORT)));
        ft.groupingCriteria = new GroupingConstraint(FLAG_FIGHTER, 2, Integer.MAX_VALUE,
                ms -> true,
                (ms0, ms1) -> ms0.getChassis().equals(ms1.getChassis()));
        allFormationTypes.put(ft.name, ft);                
    }

    /**
     * base class for limitations on formation type 
     */
    private static abstract class Constraint {
        Predicate<MechSummary> criterion;
        
        protected Constraint(Predicate<MechSummary> criterion) {
            this.criterion = criterion;
        }
        
        public abstract int getMinimum(int unitSize);
    }
    
    private static class CountConstraint extends Constraint {
        int count;
        
        public CountConstraint(int min, Predicate<MechSummary> criterion) {
            super(criterion);
            count = min;
        }
        
        @Override
        public int getMinimum(int unitSize) {
            return count;
        }
    }
    
    private static class MaxCountConstraint extends CountConstraint {
        
        public MaxCountConstraint(int max, Predicate<MechSummary> criterion) {
            super(max, ms -> !criterion.test(ms));
        }
        
        @Override
        public int getMinimum(int unitSize) {
            return unitSize - count;
        }
    }
    
    private static class PercentConstraint extends Constraint {
        double pct;
        
        public PercentConstraint(double min, Predicate<MechSummary> criterion) {
            super(criterion);
            pct = min;
        }
        
        @Override
        public int getMinimum(int unitSize) {
            return (int)Math.ceil(pct * unitSize);
        }
    }
    
    @SuppressWarnings("unused")
    private static class MaxPercentConstraint extends PercentConstraint {
        
        public MaxPercentConstraint(double max, Predicate<MechSummary> criterion) {
            super(max, ms -> !criterion.test(ms));
        }
        
        @Override
        public int getMinimum(int unitSize) {
            return unitSize - (int)Math.ceil(pct * unitSize);
        }
    }
    
    /*
     * Permits additional constraints applied to a specific subset of the units.
     * Used to force pairs (or larger groups) of units that are identical or have the same base
     * chassis.
     */
    public static class GroupingConstraint {
        int unitTypes = FLAG_ALL;
        int groupSize = 2;
        int numGroups = 1;
        Predicate<MechSummary> generalConstraint;
        BiFunction<MechSummary,MechSummary,Boolean> groupConstraint;
        
        public GroupingConstraint(Predicate<MechSummary> generalConstraint,
                BiFunction<MechSummary,MechSummary,Boolean> groupConstraint) {
            this.generalConstraint = generalConstraint;
            this.groupConstraint = groupConstraint;            
        }
        
        public GroupingConstraint(int unitTypes,
                Predicate<MechSummary> generalConstraint,
                BiFunction<MechSummary,MechSummary,Boolean> groupConstraint) {
            this(generalConstraint, groupConstraint);
            this.unitTypes = unitTypes;
        }
        
        public GroupingConstraint(int unitTypes, int groupSize, int numGroups,
                Predicate<MechSummary> generalConstraint,
                BiFunction<MechSummary,MechSummary,Boolean> groupConstraint) {
            this(generalConstraint, groupConstraint);
            this.unitTypes = unitTypes;
            this.groupSize = groupSize;
            this.numGroups = numGroups;
        }
        
        public boolean appliesTo(int unitType) {
            return ((1 << unitType) & unitTypes) != 0;
        }
    }
}
