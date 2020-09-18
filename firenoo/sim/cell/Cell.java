package firenoo.sim.cell;

import firenoo.lib.buffer.IntBoundBuffer;
import firenoo.lib.buffer.DoubleBoundBuffer;
import firenoo.lib.buffer.IBoundedBuffer;
import firenoo.lib.structs.Queue;
import firenoo.lib.data.SaveHelper;
import firenoo.dna.IDna;
import firenoo.dna.Dna;
import firenoo.dna.IDnaLoader;
import firenoo.dna.DnaLoader;
import firenoo.dna.IDnaWriter;
import firenoo.dna.DnaWriter;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Random;
import java.util.function.Consumer;



import firenoo.sim.env.ITile;

public class Cell implements ICell {

    private int age;
    private int level;
    private final int MAX_LEVEL;
    private int starveCounter;
    private IBoundedBuffer<Double> food;
    private IBoundedBuffer<Integer> growthProgress;
    private IDna dna;
    private IRibosome ribosome;
    private ITile tile;
    private Random random;
    private ICellBehavior behavior;
    private int cycle;


    public Cell(IDna dna, ITile tile, ICellBehavior behavior, int cycle) {
        this(0, 0, 10, 0, 0, 0, dna, tile, behavior, cycle);
    }

    private Cell(int age,
                 int level,
                 int maxLevel,
                 int starveCounter,
                 double food,
                 int growthProgress,
                 IDna dna,
                 ITile tile,
                 ICellBehavior behavior,
                 int cycle) {
        this.age = age;
        this.level = level;
        this.MAX_LEVEL = maxLevel;
        this.starveCounter = starveCounter;
        this.dna = dna;
        this.ribosome = new RiboImpl(dna);
        this.food = new DoubleBoundBuffer(0, ribosome.getFoodStorage());
        this.food.set(food);
        this.growthProgress = new IntBoundBuffer(0, getGrowthLevel());
        this.growthProgress.set(growthProgress);
        this.tile = tile;
        this.behavior = behavior;
        this.cycle = cycle;
        this.behavior.setCell(this, cycle);
        this.random = new Random(dna.getSeed());
    }


    @Override
    public int age() {
        return this.age;
    }

    @Override
    public int level() {
        return this.level;
    }
    
    /**
     * @return Amount of food the cell has internally.
     */
    @Override
    public IBoundedBuffer<Double> food() {
        return this.food;
    }

    @Override
    public IBoundedBuffer<Integer> growthProgress() {
        return this.growthProgress;
    }

    @Override
    public IDna dna() {
        return this.dna;
    }

    @Override
    public IRibosome ribosome() {
        return this.ribosome;
    }

    @Override
    public void setRibosome(IRibosome ribosome) {
        this.ribosome = ribosome;
    }

    @Override
    public void moveTo(ITile tile) {
        this.tile = tile;
    }

    @Override
    public Random getRandom() {
        return random;
    }

    @Override
    public int onCycleUpdate(int globalTime) {
        return 0;
    }


    public ICellBehavior getBehavior() {
        return behavior;
    }

    public ITile getTile() {
        return tile;
    }

    @Override
    public void setStarveCounter(int value) {
        this.starveCounter = value;
    }

    @Override
    public int getStarveCounter() {
        return starveCounter;
    }

    public void serialize(OutputStream stream) throws IOException {
        SaveHelper.writeInt(age, stream);
        SaveHelper.writeInt(level, stream);
        SaveHelper.writeInt(MAX_LEVEL, stream);
        SaveHelper.writeInt(starveCounter, stream);
        SaveHelper.writeDouble(food.value(), stream);
        SaveHelper.writeInt(growthProgress.value(), stream);
        SaveHelper.writeInt(cycle, stream);
        IDnaWriter writer = new DnaWriter();
        writer.write(dna, stream);
        
    }

    public static ICell deserialize(ITile tile, InputStream stream) throws IOException {
        int age = SaveHelper.readInt(stream);
        int level = SaveHelper.readInt(stream);
        int maxLevel = SaveHelper.readInt(stream);
        int starveCounter = SaveHelper.readInt(stream);
        double food = SaveHelper.readDouble(stream);
        int growthProgress = SaveHelper.readInt(stream);
        int cycle = SaveHelper.readInt(stream);
        IDnaLoader loader = new DnaLoader();
        IDna dna = loader.load(stream);
        ICellBehavior behavior = new BehaviorPassive(0);
        return new Cell(age, level, maxLevel, starveCounter, food, growthProgress, dna, tile, behavior, cycle);
    }

    /**
     * Get the amount of growth needed to level up.
     */
    private int getGrowthLevel() {
        int growthSpeed = ribosome.getGrowthSpeed();
        return (int) (Math.pow(2 - growthSpeed / 1024, level) + (10 - growthSpeed / 32)) * 100;
    }

    /**
     * Private methods are marked with usage tags, indicated after the underscore.
     * <p> B: Binary Allele</p>
     * <p> M: (Multi) N-Allele</p>
     * <p> C: Continous Domain </p>
     * <p> Di: Discrete Domain </p>
     */
    public static class RiboImpl implements IRibosome {

        private IDna dna;
        private int genBonus = -1;
        private int growthFactor = -1;
        private int growthSpeed = -1;
        private int growthEff = -1;
        private int growthBonus = -1;
        private int foodStorage = -1;
        private int foodDig = -1;
        private int foodAbs = -1;
        private int endurance = -1;
        private int visRange = -1;
        private int prodBonus = -1;
        private int effBonus = -1;
    

        private int wanderer = -1;
        private int competitive = -1;
        private int rationing = -1;

        private int memSize = -1;
        private int forgetOrder = -1;
        private RiboImpl(IDna dna) {
            this.dna = dna;
        }

        @Override
        public IDna getDna() {
            return dna;
        }

        /**
         * Returns 1 iff allele1 is dominant over allele2, -1 iff allele2
         * is dominant over allele1, and 0 iff they have same dominance.
         */
        private int dominant_B(byte dom1, byte dom2) {
            if((dom1 & 0b11) > (dom2 & 0b11)) {
                //Allele1 is dominant over the 2nd
                return 1;
            } else if((dom1 & 0b11) < (dom2 & 0b11)) {
                //Alele2 is dominant over the 1st
                return -1;
            } else {
                //Same dominance
                return 0;
            }
        }

        /**
         * Gets the partial bits.
         */
        private int partialBits_BM(int... domBits) {
            int result = domBits[0] & 0b1100;
            for(int i = 1; i < domBits.length; i++) {
                result ^= domBits[i] & 0b1100;
            }
            return result >> 2;
        }
        
        /**
         * Gets the value of the trait from the alleles. 
         */
        private int partialValue_CB(int allele1, int dom1, int allele2, int dom2) {
            int result;
            switch(partialBits_BM(dom1, dom2)) {
                case 1:
                    //Partial 50/50
                    result = (allele1 + allele2) / 2;
                    break;
                case 2:
                    //Partial 67/33
                    if(highAllele_B(dom1, dom2)) {
                        result = (int) (allele1 * 0.67f + allele2 * 0.33f);
                    } else {
                        result = (int) (allele1 * 0.33f + allele2 * 0.67f);
                    }
                    break;
                case 3:
                    //Partial 75/25
                    if(highAllele_B(dom1, dom2)) {
                        result = (int) (allele1 * 0.75f + allele2 * 0.25f);
                    } else {
                        result = (int) (allele1 * 0.25f + allele2 * 0.75f);
                    }
                    break;
                default:
                    //Codominance; honestly should be impossible for a continous set.
                    //Well then I'll just add the speeds because I don't know what to
                    //put here
                    result = allele1 + allele2;
                    break;
            }
            return result;
        }



        /**
         * Return true iff allele1 has a higher order than allele2.
         * Ties by def cannot happen (that would mean codominance)
         */
        private boolean highAllele_B(int dom1, int dom2) {
            return (dom1 & 0b1100) > (dom2 & 0b1100);
        }


        private int initValue_CB(byte[] gene) {
            int result;
            int dominant = dominant_B(gene[1], gene[3]);
            if(dominant == 0) {
                result = partialValue_CB(gene[0], gene[1], gene[2], gene[3]);
            } else {
                result = dominant > 0 ? gene[0] : gene[2];
            }
            return result;
        }

        

        @Override
        public int getGeneralBonus() {
            if(genBonus == -1) {
                genBonus = initValue_CB(dna.getGene(DnaBuilder.GENB_L));
            }
            return genBonus;
        }

        @Override
        public int getGrowthFactor() {
            if(growthFactor == -1) {
                growthFactor = initValue_CB(dna.getGene(DnaBuilder.GRTF_L));
            }
            return growthFactor;
        }

        @Override
        public int getGrowthSpeed() {
            if(growthSpeed == -1) {
                growthSpeed = initValue_CB(dna.getGene(DnaBuilder.GRTS_L));
            }
            return growthSpeed;
        }

        @Override
        public int getGrowthEfficiency() {
            if(growthEff == -1) {
                growthEff = initValue_CB(dna.getGene(DnaBuilder.GRTE_L));
            }
            return growthEff;
        }

        @Override
        public int getGrowthBonus() {
            if(growthBonus == -1) {
                growthBonus = initValue_CB(dna.getGene(DnaBuilder.GRTB_L));
            }
            return growthBonus;
        }

        @Override
        public int getFoodStorage() {
            if(foodStorage == -1) {
                foodStorage = initValue_CB(dna.getGene(DnaBuilder.FDST_L));
            }
            return foodStorage;
        }

        @Override
        public int getFoodDigestion() {
            if(foodDig == -1) {
                foodDig = initValue_CB(dna.getGene(DnaBuilder.FDDI_L));
            }
            return foodDig;
        }

        @Override
        public int getFoodAbsorption() {
            if(foodAbs == -1) {
                foodAbs = initValue_CB(dna.getGene(DnaBuilder.FDAB_L));
            }
            return foodAbs;
        }

        @Override
        public int getEndurance() {
            if(endurance == -1) {
                endurance = initValue_CB(dna.getGene(DnaBuilder.ENDU_L));
            }
            return endurance;
        }

        @Override
        public int getVisionRange() {
            if(visRange == -1) {
                visRange = initValue_CB(dna.getGene(DnaBuilder.VSRG_L));
            }
            return visRange;
        }

        /**
         * Get the contribution value of the allele, with partial rank {@code pRank} and
         * set size {@code setSize}.
         * @param allele  allele value
         * @param pRank   partial rank (k)
         * @param setSize set size (n)
         * @return the contribution value. Formula is (9/(19n^3)) * (k^2-k-2kn+n+3n^2+1/9).
         */
        private int prodOrEff(int allele, int pRank, int setSize) {
            return allele * (9 / (19 * setSize * setSize * setSize)) * (pRank * pRank - pRank - 2 * pRank * setSize + setSize + 3 * setSize * setSize + (1/9));
        }

        private int initProdOrEff(byte[] alleles, byte[] domBits) {
            int result = 0;
            int domRank = 0;
            int i;
            //Dominance Check
            Queue<Integer> selected = new Queue<>(domBits.length);
            for(i = 0; i < alleles.length; i++) {
                int b = domBits[i];
                if((b & 0b11) > domRank) {
                    domRank = b & 0b11;
                    selected.clear();
                    selected.enqueue(i);
                } else if((b & 0b11) == domRank) {
                    selected.enqueue(i);
                }
            }
            //Partial Check
            int[] pRanks = new int[4];
            int pRankCt = 0;
            i = 0;
            int partialType = selected.peek() & 0b1100;
            int[] selectedArr = new int[selected.elementCt()];
            while(!selected.isEmpty()) {
                int s = selected.dequeue();
                selectedArr[i] = s;
                int pBits = domBits[s] & 0b1100;
                partialType ^= pBits;
                if(pRanks[pBits >>> 2] == 0) {
                    pRankCt++;
                }
                pRanks[pBits >>> 2]++;
                i++;
            }
            partialType >>>= 2;
            if(partialType == 0) {
                //Codominance. We'll just get the average of all the alleles.
                for(i = 0; i < selectedArr.length; i++) {
                    result += alleles[selectedArr[i]];
                }
                result /= selectedArr.length;
            } else {
                //Partial
                int curPRank = 0;
                for(i = 0; i < 4; i++) {
                    int contribs = 0;
                    if(pRanks[i] != 0) {
                        curPRank++;
                        for(int j = 0; j < selectedArr.length; j++) {
                            if((domBits[selectedArr[j]] & 0b1100) >> 2 == i) {
                                contribs += alleles[selectedArr[j]] / pRanks[i];
                            }
                        }
                    }
                    result += prodOrEff(contribs, curPRank, pRankCt);
                }
            }
            

            return result;
        }

        /**
         * Production bonus is mutually exclusive with Energy Efficiency; both rely
         * on the same set of alleles.
         */
        @Override
        public int getProductBonus() {
            if(prodBonus == -1) {
                byte[] gene1 = dna.getGene(DnaBuilder.PEF1_L);
                byte[] gene2 = dna.getGene(DnaBuilder.PEF2_L);
                int p1 = dominant_B(gene1[1], gene2[1]);
                int p2 = dominant_B(gene1[3], gene2[3]);
                if(p1 == 0 && p2 != 0) {
                    prodBonus = initProdOrEff(new byte[] {gene1[1], gene1[3], gene2[1], gene2[3]}, new byte[] {gene1[0], gene1[2], gene2[0], gene2[2]});
                } else {
                    prodBonus = 0;
                }
            }
            return prodBonus;
        }

        /**
         * Energy Efficiency is mutually exclusive with Production bonus; both rely
         * on the same set of alleles.
         */
        @Override
        public int getEffBonus() {
            if(effBonus == -1) {
                byte[] gene1 = dna.getGene(DnaBuilder.PEF1_L);
                byte[] gene2 = dna.getGene(DnaBuilder.PEF2_L);
                int p1 = dominant_B(gene1[1], gene2[1]);
                int p2 = dominant_B(gene1[3], gene2[3]);
                if(p1 != 0 && p2 == 0) {
                    effBonus = initProdOrEff(new byte[] {gene1[1], gene1[3], gene2[1], gene2[3]}, new byte[] {gene1[0], gene1[2], gene2[0], gene2[2]});
                } else {
                    return 0;
                }
            }
            return effBonus;
        }

        /**
         * If both wanderer and competitive is present, the effect of one reduces the other, to a
         * minimum of 0.
         */
        @Override
        public int isWanderer() {
            if(wanderer == -1) {
                wanderer = initValue_CB(dna.getGene(DnaBuilder.WAND_L));
                //max penalty strength is 50%.
                float penalty = Math.abs(((byte) initValue_CB(dna.getGene(DnaBuilder.CMPT_L))) / 255f);
                wanderer *= penalty;
            }
            return wanderer;
        }

        @Override
        public int isCompetitive() {
            if(competitive == -1) {
                competitive = initValue_CB(dna.getGene(DnaBuilder.CMPT_L));
                //max penalty strength is 50%.
                float penalty = Math.abs(((byte) initValue_CB(dna.getGene(DnaBuilder.WAND_L))) / 255f);
                competitive *= penalty;
            }
            return competitive;
        }

        @Override
        public int isRationing() {
            if(rationing == -1) {
                rationing = initValue_CB(dna.getGene(DnaBuilder.RATN_L));
            }
            return rationing;
        }

        @Override
        public int getMemSize() {
            if(memSize == -1) {
                memSize = initValue_CB(dna.getGene(DnaBuilder.MEMS_L));
            }
            return memSize;
        }

        @Override
        public int getForgetOrder() {
            if(forgetOrder == -1) {
                forgetOrder = initValue_CB(dna.getGene(DnaBuilder.MEMF_L));
            }
            return forgetOrder;
        }

        /**
         * Creates a dna data structure pre-loaded with values.
         */
        public static class DnaBuilder {

            private IDna dna;

            //The traits
            public int[] traits;

            public static final int TRAIT_COUNT = 16;

            //Trait indices
            public static final int GENB_L = 0; //General bonus
            public static final int GRTF_L = 1; //Growth factor
            public static final int GRTS_L = 2; //Growth speed
            public static final int GRTE_L = 3; //Growth efficiency
            public static final int GRTB_L = 4; //Growth bonus
            public static final int FDST_L = 5; //Food storage
            public static final int FDDI_L = 6; //Food digestion
            public static final int FDAB_L = 7; //food absorption
            public static final int ENDU_L = 8; //Endurance
            public static final int VSRG_L = 9; //Vision range
            public static final int WAND_L = 10; //Wanderer
            public static final int CMPT_L = 11; //Competitive
            public static final int RATN_L = 12; //Rationing
            public static final int PEF1_L = 13; //Prod/Eff 1
            public static final int PEF2_L = 14; //Prod/Eff 2
            public static final int MEMS_L = 15; //Memory size and forgetOrder
            public static final int MEMF_L = 16; //Memory forget policy
            public static final int PARTIAL_5050_A = 0b00001100;
            public static final int PARTIAL_5050_B = 0b00001000;
            public static final int PARTIAL_6733_A = 0b00001000;
            public static final int PARTIAL_6733_B = 0b00000000;
            public static final int PARTIAL_7525_A = 0b00001100;
            public static final int PARTIAL_7525_B = 0b00000000;

            public DnaBuilder(long seed) {
                this.dna = new Dna(TRAIT_COUNT * 4, seed);
                this.traits = new int[TRAIT_COUNT];
            }

            public void with(Consumer<DnaBuilder> func) {
                func.accept(this);
            }

            /**
             * Creates a binary allele gene at the specified location in the array.
             * @param allele1  first allele
             * @param dom1     raw dominance byte for allele1
             * @param allele2  second allele
             * @param dom2     raw dominance byte for allele2
             * @param loc      index of where to write in the array. Must be < 10.
             * @return this builder
             */
            public DnaBuilder createGene(byte allele1, byte dom1, byte allele2, byte dom2, int loc) {
                int result = 0;
                result |= ((int) dom2 << 24);
                result |= ((int) allele2 << 16);
                result |= ((int) dom1 << 8);
                result |=  (int) allele1;
                traits[loc] = result;
                return this;
            }

            public int withRank(int domBits, int rank) {
                domBits &= ~0b11;
                return domBits | (rank & 0b11);
            }

            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 50/50.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder genBonus(int allele1, int allele2) {
                return genBonus(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }
            
            /**
             * Add general bonus. All values are casted to bytes.
             * @param allele1  byte value for allele1
             * @param dom1     the raw dominance byte for allele1
             * @param allele2  byte value for allele2
             * @param dom2     the raw dominance byte for allele2
             */
            public DnaBuilder genBonus(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, GENB_L);
            }

            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 50/50.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder growthFactor(int allele1, int allele2) {
                return growthFactor(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            /**
             * Add growth factor. All values are casted to bytes.
             * @param allele1   byte value for allele1
             * @param dom1      raw dominance byte for allele1
             * @param allele2   byte value for allele2
             * @param dom2      raw dominance byte for allele2
             */
            public DnaBuilder growthFactor(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, GRTF_L);
            }

            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 50/50.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder growthSpeed(int allele1, int allele2) {
                return growthSpeed(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            /**
             * Add growth speed. All values are casted to bytes. 
             * @param allele1   byte value for allele1
             * @param dom1      raw dominance byte for allele1
             * @param allele2   byte value for allele2
             * @param dom2      raw dominance byte for allele2
             */
            public DnaBuilder growthSpeed(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, GRTS_L);
            }
            
            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 67/33.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder growthEfficiency(int allele1, int allele2) {
                return growthEfficiency(allele1, PARTIAL_6733_A, allele2, PARTIAL_6733_B);
            }

            public DnaBuilder growthEfficiency(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, GRTE_L);
            }

            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 50/50.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder growthBonus(int allele1, int allele2) {
                return growthBonus(allele1, PARTIAL_6733_A, allele2, PARTIAL_6733_B);
            }

            public DnaBuilder growthBonus(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte)allele1, (byte) dom1, (byte) allele2, (byte) dom2, GRTB_L);
            }

            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 50/50.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder foodStorage(int allele1, int allele2) {
                return foodStorage(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder foodStorage(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, FDST_L);
            }

            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 50/50.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder foodDigestion(int allele1, int allele2) {
                return foodDigestion(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder foodDigestion(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, FDDI_L);
            }

            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 50/50.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder foodAbsorption(int allele1, int allele2) {
                return foodAbsorption(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder foodAbsorption(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, FDAB_L);
            }

            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 75/25.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder endurance(int allele1, int allele2) {
                return endurance(allele1, PARTIAL_7525_A, allele2, PARTIAL_7525_B);
            }

            public DnaBuilder endurance(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, ENDU_L);
            }

            /**
             * All values are casted to bytes. Both dominance ranks are set
             * to 0. Default Dominance behavior is Partial 50/50.
             * @param allele1 byte value for allele1. This allele is the higher allele.
             * @param allele2 byte value for allele2
             */
            public DnaBuilder visionRange(int allele1, int allele2) {
                return visionRange(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder visionRange(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, VSRG_L);
            }





            public DnaBuilder wanderer(int allele1, int allele2) {
                return wanderer(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder wanderer(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, WAND_L);
            }

            public DnaBuilder competitive(int allele1, int allele2) {
                return competitive(allele1, PARTIAL_6733_A, allele2, PARTIAL_6733_B);
            }

            public DnaBuilder competitive(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, CMPT_L);
            }

            public DnaBuilder rationing(int allele1, int allele2) {
                return rationing(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder rationing(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, RATN_L);
            }

            public DnaBuilder prodEff1(int allele1, int allele2) {
                return prodEff1(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder prodEff1(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, PEF1_L);
            }

            public DnaBuilder prodEff2(int allele1, int allele2) {
                return prodEff2(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder prodEff2(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, PEF2_L);
            }

            /**
             * Sets the dominance bits of PEF1 and PEF2 such that production bonus is active.
             * Production Bonus is active ony iff allele1 of both genes are not codominant and allele2
             * of both genes are codominant (with respect to each other).
             * @param useFlag  Set to 0 to turn off.
             *                 Set to 1 to use allele2 of PEF1, and set to 2 to
             *                 use allele2 of PEF2. Other values are treated as
             *                 0.
             */
            public DnaBuilder prodBonus(int useFlag) {
                int msc1 = this.traits[PEF1_L];
                int msc2 = this.traits[PEF2_L];
                //Clear dominance bits.
                msc1 &= 0xFCFFFCFF;
                msc2 &= 0xFCFFFCFF;
                //dominance values
                msc1 |= 0b11 << 8;
                msc2 |= 0b11 << 8;
                if(useFlag == 1) {
                    msc1 |= 0b11 << 24;
                } else if(useFlag == 2) {
                    msc2 |= 0b11 << 24;
                } else {
                    msc1 |= 0b01 << 24; //default for partial 50/50
                }
                this.traits[PEF1_L] = msc1;
                this.traits[PEF2_L] = msc2;
                return this;
            }

            /**
             * Sets the dominance bits of PEF1 and PEF2 such that energy efficiency is active.
             * Efficiency bonus is active only when allele1 of both genes are not codominant and allele2
             * of both genes are codominant (with respect to each other).
             * @param useFlag  Set to 0 to use allele2 of neither MSC gene. Set to 1 to use allele2
             *                 of MSC1, and set to 2 to use allele2 of MSC2. Other values are treated as
             *                 0.
             */
            public DnaBuilder effBonus(int useFlag) {
                int msc1 = this.traits[PEF1_L];
                int msc2 = this.traits[PEF2_L];
                //Clear dominance bits.
                msc1 &= 0xFCFFFCFF;
                msc2 &= 0xFCFFFCFF;
                //dominance values
                msc1 |= 0b11 << 24;
                msc2 |= 0b11 << 24;
                if(useFlag == 1) {
                    msc1 |= 0b11 << 8;
                } else if(useFlag == 2) {
                    msc2 |= 0b11 << 8;
                } else {
                    msc1 |= 0b01 << 8; //default for partial 50/50
                }
                this.traits[PEF1_L] = msc1;
                this.traits[PEF2_L] = msc2;
                return this;
            }

            /**
             * Number of blocks of memory to be used.
             */
            public DnaBuilder memorySize(int allele1, int allele2) {
                return memorySize(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder memorySize(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, MEMS_L);
            }

            /**
             * Determines the order
             */
            public DnaBuilder forgetOrder(int allele1, int allele2) {
                return forgetOrder(allele1, PARTIAL_5050_A, allele2, PARTIAL_5050_B);
            }

            public DnaBuilder forgetOrder(int allele1, int dom1, int allele2, int dom2) {
                return createGene((byte) allele1, (byte) dom1, (byte) allele2, (byte) dom2, MEMF_L);
            }

            public IDna build() {
                dna.append(traits);
                return dna;
            }

        }
        
    }
}

