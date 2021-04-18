/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ai.synthesis.localsearch.searchImplementation;

import ai.synthesis.DslLeague.Runner.SettingsAlphaDSL;
import ai.synthesis.Novelty.Calculator.NoveltyCommandSingleton;
import ai.synthesis.dslForScriptGenerator.DSLCommandInterfaces.ICommand;
import ai.synthesis.grammar.dslTree.builderDSLTree.BuilderDSLTreeSingleton;
import ai.synthesis.grammar.dslTree.interfacesDSL.iDSL;
import ai.synthesis.grammar.dslTree.utils.ReduceDSLController;
import ai.synthesis.runners.roundRobinLocal.SmartRRGxGRunnable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author rubens
 */
public class SimAnComposedNoveltyDefault  extends SearchImplementationAbstract{

    private List<String> scripts;
    private List<Float> winners;
    private String uniqueID = UUID.randomUUID().toString();    
    private NoveltyCommandSingleton novelInstance = NoveltyCommandSingleton.getInstance();

    static Random rand = new Random();

    public List<String> getScripts() {
        return scripts;
    }

    public List<Float> getWinners() {
        return winners;
    }

    
    public DetailedSearchResult run(HashSet<iDSL> sc_base, iDSL sc_improve, int n_steps, float score) {        
        //System.out.println(uniqueID+" S.A. Script base = " + sc_base.translate()
        //        + "\nS.A. Script to improve = " + sc_improve.translate() + "\n");
        DetailedSearchResult results = new DetailedSearchResult();
        //SimulatedAnnealing settings
        iDSL best_sol = (iDSL) sc_improve.clone();
        float best_score = score;
        float temp_current = SettingsAlphaDSL.get_current_temperature();
        float temp_final = SettingsAlphaDSL.get_final_temperature();
        float alpha = SettingsAlphaDSL.get_alpha();
        //search
        while (temp_current >= temp_final) {
            for (int i = 0; i < n_steps; i++) {
                iDSL sc_neighbour = (iDSL) sc_improve.clone();
                sc_neighbour = get_neighbour_configurated(sc_neighbour, temp_current);
                
                //System.out.println(uniqueID+"    ** Neighbour =" + sc_neighbour.translate());
                //System.out.println(uniqueID+"    ** base =" + sc_base.translate());
                int w_novelty = novelInstance.get_novelty_factor(sc_neighbour, temp_current);
                float new_score = 0.0f;                
                try {
                    new_score = evaluate_list_enemies(sc_base, sc_neighbour);  
                    novelInstance.add_commands(sc_neighbour);                    
                } catch (InterruptedException ex) {
                    Logger.getLogger(SimAnComposedNoveltyDefault.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ExecutionException ex) {
                    Logger.getLogger(SimAnComposedNoveltyDefault.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (new_score == 4.0f) {
                    best_sol = (iDSL) sc_neighbour.clone();
                    best_score = new_score;
                    results.setWinner(best_score);
                    results.setsBase(((iDSL) sc_base.toArray()[0]));
                    results.setsWinner((iDSL) best_sol.clone());
                    //System.out.println(uniqueID+"    #### Best Score reached! "+sc_base+"  "+best_sol.translate());
                    return results;
                }
                if(w_novelty <= SettingsAlphaDSL.get_novelty_size()){                                                     
                    sc_improve = (iDSL) sc_neighbour.clone();
                    score = new_score;
                    if (score > best_score) {
                        best_sol = (iDSL) sc_improve.clone();
                        best_score = score;
                    }
                } else {
                    //temperature
                    double uniValue = Math.random();
                    float delta = score - new_score;
                    if (uniValue < Math.exp(-delta / temp_current)) {
                        if (!sc_neighbour.translate().equals("")) {
                            sc_improve = (iDSL) sc_neighbour.clone();
                            score = new_score;
                        }
                    }
                }
            }
            temp_current = temp_current * (1 - alpha);
            //System.out.println("temperature going doing..." + temp_current);
            //System.out.println("Script improve: " + sc_improve.translate());
        }
        results.setWinner(best_score);
        results.setsBase((iDSL) sc_base.toArray()[0]);
        results.setsWinner(best_sol);
        //System.out.println(uniqueID+"    #### Normal execution Finished!!");
        return results;
    }

    @Override
    public DetailedSearchResult run(iDSL sc_base, iDSL sc_improve, int n_steps, float winner) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private float evaluate_list_enemies(HashSet<iDSL> sc_base, iDSL sc_neighbour) throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        float new_score = 0.0f;
        Collection<Callable<resultsThreads>> tasks = new ArrayList<>();
        for (iDSL dSL : sc_base) {
            //new_score += evaluate_thread_scripts(dSL, sc_neighbour);   
            callableMultiThreadsBattle t = new callableMultiThreadsBattle(dSL, sc_neighbour);
            tasks.add(t);
        }
        List<Future<resultsThreads>> taskFutureList =executor.invokeAll(tasks);
        HashSet<ICommand> uniqueCommands = new HashSet<>();
        for (Future<resultsThreads> future : taskFutureList) {
            new_score += future.get().result;
            uniqueCommands.addAll(future.get().uniqueCommands);
        }
        executor.shutdown();
        ReduceDSLController.removeUnactivatedParts(sc_neighbour, new ArrayList<>(uniqueCommands));
        return new_score/(float)sc_base.size();
         
    }
}
