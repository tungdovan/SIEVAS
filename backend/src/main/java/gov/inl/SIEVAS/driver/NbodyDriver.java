/* 
 * Copyright 2017 Idaho National Laboratory.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.inl.SIEVAS.driver;

import gov.inl.SIEVAS.DAO.CriteriaBuilderCriteriaQueryRootTriple;
import gov.inl.SIEVAS.DAO.NbodyDAO;
import gov.inl.SIEVAS.DAO.NbodyInfoDAO;
import gov.inl.SIEVAS.common.DriverOption;
import gov.inl.SIEVAS.common.IDriver;
import gov.inl.SIEVAS.entity.Nbody;
import gov.inl.SIEVAS.entity.NbodyInfo;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.springframework.context.ApplicationContext;

/**
 * Class to load Nbody data
 * @author monejh
 */
public class NbodyDriver implements IDriver
{

    private NbodyDAO nbodyDAO;
    private NbodyInfoDAO nbodyInfoDAO;
    private NbodyInfo nbodyInfo;
    private long nbodyInfoId = 1L;
    private CriteriaBuilderCriteriaQueryRootTriple<Nbody,Nbody> triple;
    private CriteriaBuilder cb;
    private Root<Nbody> root;
    private Order[] orderBy = new Order[2];
    private NBodyGenerator generator = new NBodyGenerator();
    private long maxSteps = 0;
    
    /***
     * Init the driver
     * @param context Context to use
     * @param options The list of options
     */
    @Override
    public void init(ApplicationContext context, List<DriverOption> options)
    {
        this.nbodyInfoDAO = context.getBean(NbodyInfoDAO.class);
        this.nbodyDAO = context.getBean(NbodyDAO.class);
        
        this.nbodyInfo = nbodyInfoDAO.findById(nbodyInfoId);
        
        triple = nbodyDAO.getCriteriaTriple();
        cb = triple.getCriteriaBuilder();
        root = triple.getRoot();
        orderBy[0] = cb.asc(root.get("step"));
        orderBy[1] = cb.asc(root.get("planetNumber"));
        
        
        CriteriaBuilder cb2 = nbodyDAO.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query2 = cb2.createQuery(Long.class);
        Root<Nbody> root2 = query2.from(Nbody.class);
        query2.select(cb2.max(root2.get("step")));
        Long maxStep = nbodyDAO.getEntityManager().createQuery(query2).getSingleResult();
        if (maxStep!=null)
            maxSteps = maxStep.longValue();
    }
    
    
    @Override
    public double getStartTime()
    {
        return 0.0;
    }
    
    @Override
    public double getEndTime()
    {
        return maxSteps*nbodyInfo.getTimestep();
    }
    
    /***
     * Gets the data for a timestep
     * @param startTime The start time
     * @param timestep The timestep
     * @param resolution The resolution of objects returned.
     * @param maxResults The maximum results returned.
     * @return The list of nbody objects
     */
    @Override
    public List getData(double startTime, double timestep, double resolution, long maxResults)
    {
        
        long startTimestep = (long) Math.floor(startTime/nbodyInfo.getTimestep());
        long stopTimestep = (long) Math.floor((startTime+timestep)/nbodyInfo.getTimestep());
        
        //Predicate pred = cb.and(cb.ge(root.get("step"), startTimestep),cb.le(root.get("step"), stopTimestep));
        //List<Nbody> list = nbodyDAO.findByCriteria(triple, orderBy, 0, -1, pred);
       
        //get the max step based on start
        CriteriaBuilder cb2 = nbodyDAO.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query2 = cb2.createQuery(Long.class);
        Root<Nbody> root2 = query2.from(Nbody.class);
        query2.select(cb2.max(root2.get("step")));
        Predicate maxPred = cb2.le(root2.get("step"), startTimestep);
        query2.where(maxPred);
        Long maxStep = nbodyDAO.getEntityManager().createQuery(query2).getSingleResult();
        Predicate pred2 = cb.equal(root.get("step"), maxStep);
        List<Nbody> listPrior = nbodyDAO.findByCriteria(triple, orderBy, 0, -1, pred2);
        
        double startGenTime = maxStep*nbodyInfo.getTimestep();
        //double h = Math.pow(0.5, 14); //can change to 14 or 16 to change time step
        //List<Nbody> results = generator.run(startGenTime, startTime+timestep, listPrior, nbodyInfo.getTimestep(), maxStep);
        List<Nbody> results = listPrior;
        
        //remove front steps not needed
        int stepsToSkip = (int)Math.floor((startTime - startGenTime)/nbodyInfo.getTimestep());
        
        //results = results.subList(stepsToSkip*10, results.size());
        //now remove step smaller than resolution
        //TODO
        
        int itemsToSkip = 0; //(results.size()/10)/10;
        List<Nbody> list = new ArrayList<>();//((int)(results.size()/itemsToSkip)*10);
        
        
        for(int ii=0;ii<results.size();ii++)
        {
            Nbody nbody = results.get(ii);
            nbody.setTime(nbody.getStep()*nbodyInfo.getTimestep());
            list.add(nbody);
        }
        /*for(int jj=Math.max(0,stepsToSkip*10-1); jj<results.size(); jj+= itemsToSkip*10)
        {
            for(int ii=0;ii<10;ii++)
            {
                Nbody nbody = results.get(jj + ii);
                if (nbody!=null)
                {
                    //set time for DVR engine
                    nbody.setTime(nbody.getStep()*nbodyInfo.getTimestep());    
                    list.add(nbody);
                }
            }
            
        }*/
        //now run solution between each point
        //TODO
        
        //DONE
        
        return list;
    }
    
    @Override
    public void shutdown()
    {
        
    }
    
}
