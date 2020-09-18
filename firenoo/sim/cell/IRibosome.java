package firenoo.sim.cell;

import firenoo.dna.IDna;

/**
 * Acts as the Dna interpreter. Contains methods for interpreting raw dna
 * data. A default implementation is provided.
 */
public interface IRibosome {

    static final int GENERAL_BONUS = 0;

    IDna getDna();

    /**
     * Stat that grants bonuses to all other stats.
     * Default Dominance: Partial 50/50
     */
    int getGeneralBonus();

    /**
     * Stat that determines the base stats of growth. This stat is used as
     * a base for the other growth stats.
     */
    int getGrowthFactor();

    /**
     * Stat that determines the amount of growth needed to level up.
     */
    int getGrowthSpeed();

    /**
     * Stat that determines the amount of growth gained per unit food digested.
     */
    int getGrowthEfficiency();

    /**
     * Stat that determines the amount of growth the cell carries over on
     * a level up.
     */
    int getGrowthBonus();

    /**
     * Stat that determines the amount of food that can be absorbed every
     * metabolism cycle.
     */
    int getFoodAbsorption();

    /**
     * Stat that determines the rate at which food is converted to growth.
    int getFoodDigestion();
    */
    int getFoodDigestion();

    /**
     * Stat that determines the amount of food that can be stored in the
     * cell's stomachs (vacuoles)
     */
    int getFoodStorage();


    /**
     * Stat that determines how long the cell can starve before dying.
     */
    int getEndurance();


    /**
     * Stat that determines the cell's vision range, in tiles (Taxicab dist).
     */
    int getVisionRange();

    /**
     * A bonus that is applied to the production speed if present. While the returned
     * integer cannot be negative, the bonus amount itself CAN be negative.
     * Production bonus and Energy efficiency are mutually exclusive.
     */
    int getProductBonus();
    
    /**
     * A bonus that is applied to the efficiency of converting food to waste. Increased
     * efficiency means less waste... While the returned integer cannot be negative, the bonus
     * amount itself CAN be negative.
     * Production bonus and Energy efficiency are mutually exclusive.
     */
    int getEffBonus();

    /**
     * Behavorial trait. Cell will wander even if it is content. Values indicate the likeliness
     * of wandering.
     * Wandering and competitive traits reduce each other's potency if both are present.
     */
    int isWanderer();

    /**
     * Behavorial trait. Cell will gain bonuses to its stats if it detects the presence of
     * other cells, but lose stats if alone. Values indicate the bonus as well as the scaling
     * with more than 1 cell close by.
     * Wandering and competitive traits reduce each other's potency if both are present.
     */
    int isCompetitive();

    /**
     * Behavorial trait. Cell will not grow at full potential until its food storage fills to
     * some amount. Values indicate the rationing type, how much is rationed, and when to start
     * growing at full potential.
     */
    int isRationing();

    /**
     * Determines the number of memory blocks available for storage. Once the number of memory blocks
     * reaches this number, no new memory blocks can be formed. Instead, they must be reallocated.
     */
    int getMemSize();

    /**
     * Determines the order in which memory is erased. Different values determine the order in which
     * memory is deleted. Some examples include:
     * <ul>
     *   <li>Forget least used memory blocks first</li>
     *   <li>Forget memory blocks at random</li>
     *   <li>Forget recently used memory blocks first</li>
     *   <li>Forget memory blocks of furthest physical distance first </li>
     * </ul>
     */
    int getForgetOrder();
}