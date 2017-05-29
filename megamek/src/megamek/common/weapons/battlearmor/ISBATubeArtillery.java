/**
 * MegaMek - Copyright (C) 2004,2005 Ben Mazur (bmazur@sev.org)
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 */
/*
 * Created on Oct 20, 2004
 *
 */
package megamek.common.weapons.battlearmor;

import megamek.common.AmmoType;
import megamek.common.weapons.ArtilleryWeapon;

/**
 * @author Sebastian Brocks
 */
public class ISBATubeArtillery extends ArtilleryWeapon {

    /**
     *
     */
    private static final long serialVersionUID = -2803991494958411097L;

    /**
     *
     */
    public ISBATubeArtillery() {
        super();
        name = "Tube Artillery (BA)";
        setInternalName("ISBATubeArtillery");
        rackSize = 3;
        ammoType = AmmoType.T_BA_TUBE;
        shortRange = 2;
        mediumRange = 2;
        longRange = 2;
        extremeRange = 2; // No extreme range.
        tonnage = 0.5;
        criticals = 4;
        bv = 27;
        cost = 200000;
        rulesRefs = "284, TO";
        flags = flags.or(F_BA_WEAPON).andNot(F_MECH_WEAPON).andNot(F_TANK_WEAPON);      
        techAdvancement.setTechBase(TECH_BASE_IS)
    	.setIntroLevel(false)
    	.setUnofficial(false)
        .setTechRating(RATING_E)
        .setAvailability(RATING_X, RATING_X, RATING_F, RATING_E)
        .setISAdvancement(3070, 3075, DATE_NONE, DATE_NONE, DATE_NONE)
        .setISApproximate(false, false, false, false, false)
        .setPrototypeFactions(F_CS)
        .setProductionFactions(F_CS);
    }

}
