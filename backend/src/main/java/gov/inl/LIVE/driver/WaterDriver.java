/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.inl.LIVE.driver;

import gov.inl.LIVE.common.IDriver;
import java.util.List;
import java.util.ArrayList;
import org.apache.poi.ss.usermodel.*;
import org.springframework.context.ApplicationContext;
import java.io.File;
import java.io.IOException;
import gov.inl.LIVE.entity.WaterData;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import java.lang.Math;

/**
 *
 * @author SZEWTG
 */
public class WaterDriver  implements IDriver {
    
    private static int stopColumn = 0;
    private static double scheduledTime = 0.0;
    private static double dataDelta = 0.0;
    private static int dataIndex = 0;
    private Workbook wb;
    private Sheet sheetStream;
    private Sheet sheetPipe;
    private double endTime;
    
    
    private static final int START_COLUMN_HEADER = 1;
    private static final int START_ROW_HEADER = 1;
    private static final int START_ROW_DATA = 4;
    private static final int COLUMN_OFFSET = 6;
    private static final int TEMP_COLUMN = 3;
    private static final int PRESS_COLUMN = 4;
    private static final int TIME_COLUMN = 1;
    private static final int MAX_ENTRIES = 1000;
    int count = 0;
    
    @Override
    public void init(ApplicationContext context)
    {
        // open the file
        // TODO: this should probably be a pop up and more advance checking of file could be implemented

        System.out.println("opening file");
        
        try {
            wb = WorkbookFactory.create(new File("C:/Users/SZEWTG/Downloads/water pipe dynamic result 2016 9 13.xlsx"));

        } catch (IOException ex) {
            Logger.getLogger(WaterDriver.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidFormatException ex) {
            Logger.getLogger(WaterDriver.class.getName()).log(Level.SEVERE, null, ex);
        }
            sheetStream = wb.getSheetAt(0);
            sheetPipe = wb.getSheetAt(1);
            endTime = sheetStream.getRow(sheetStream.getLastRowNum()).getCell(TIME_COLUMN).getNumericCellValue();
    }

    // startTime - the time the data should start at
    // timestep - how much to skip of the data/how fast should the play back be
    // resolution - ???
    // maxResults - ???
    @Override
    public List getData(double startTime, double timestep, double resolution, long maxResults)
    {
        System.out.println("RUNNING PARSER");  
        
        double currentSystemTime = System.currentTimeMillis();
        List<WaterData> list = new ArrayList<>();
        
        // offset for start time
        if (startTime >= 0.0d)
        {
            setDataIndex(getIndexAtTime(startTime));
            
            // set to run this data set immediately
            setScheduledTime(0.0);
            System.out.println("Setting Start Time " + startTime); 
        }
        
        double delta = currentSystemTime - getScheduledTime();
        System.out.println("System Time - Scheduled time " +  delta);
        
        // if we have reached the time for the next data to go out
        if (currentSystemTime >= getScheduledTime() )
        {
                    System.out.println("Current Index " + getDataIndex()); 
            // process pipe data
            list = setPipeData(getDataIndex(), list);

            //process stream data
            list = setStreamData(getDataIndex(), list);
               
            //update the index for next run through 
            setDataIndex(getNextDataIndex(timestep));
            
                    System.out.println("Next Index " + getDataIndex()); 
            
        }
        
        return list;
        
    }
    
    // get the current index that should be process
    private int getDataIndex()
    {
        return dataIndex;
    }
    
    // set the index that should be processed
    private void setDataIndex(int index)
    {
        dataIndex = index;
    }
    
    // given a time (with reference to data time), get the corresponding index
    private int getIndexAtTime(double time)
    {
        return (int)time*100;
    }
    
    // given an index, get the time for that index
    private double getTimeAtIndex(int index)
    {
        return sheetStream.getRow(START_ROW_DATA +  index).getCell(TIME_COLUMN).getNumericCellValue();
    }
    
    // get the time the next data should go out
    private double getScheduledTime()
    {
        return scheduledTime;
    }
    
    // set the next time data should go out
    private void setScheduledTime(double time)
    {
        scheduledTime = time;
    }
    
    private int getNextDataIndex(double speed)
    {
            int currentIndex = getDataIndex();
            int previousIndex = currentIndex - 1;

            if (currentIndex == 0)
            {
                return currentIndex + 1;
            }
            
            double currentRowTime = sheetStream.getRow(START_ROW_DATA +  currentIndex).getCell(TIME_COLUMN).getNumericCellValue();
            double previousRowTime = sheetStream.getRow(START_ROW_DATA + previousIndex).getCell(TIME_COLUMN).getNumericCellValue();

            double timeScale = 1;//3600000; hours to milliseconds
            dataDelta = ((currentRowTime - previousRowTime)/speed) * timeScale;

            System.out.println("dataDelta " + dataDelta);

            double skip;
            int nextIndex;

            // very important!!! if scheduled rate changes in DVRService, this needs to be updated as well
            // TODO: make method to get AMQ schedule rate
            double scheduleRate = 0.1;

            // if the data is required to be sent faster than the schedule rate, figure out how much data to skip
            if (dataDelta < scheduleRate && dataDelta > 0.0)
            {
                skip = scheduleRate / dataDelta;

                nextIndex = (int) Math.floor((skip + getDataIndex()));               
                setScheduledTime(0.0);
            }
            else
            {
                nextIndex = getDataIndex() + 1;
                setScheduledTime(dataDelta + System.currentTimeMillis());
            }
                
            // if we try to go past the data set, wrap around to the appropriate spot
            if (nextIndex + START_ROW_DATA >= sheetStream.getLastRowNum())
            {
                nextIndex = nextIndex - (sheetStream.getPhysicalNumberOfRows() - START_ROW_DATA);
            }
            
        return nextIndex;
    }
    
    
    // set the pipe data at the given index in the provided list
    List<WaterData> setPipeData(int index, List<WaterData> list)
    {
        // TODO: handle multiple pipe data sheets
        double pipeStartTime = sheetPipe.getRow(4).getCell(1).getNumericCellValue();
        WaterData waterData = new WaterData();

        //if the current row time for stream data is greater than  or equal to the start time of pipe data, start adding in pipe data
        if (getTimeAtIndex(index) >= pipeStartTime)
        {
            //ugh precision problems....
            int t1 =(int)(getTimeAtIndex(index) * 100);
            int t2 = (int)(pipeStartTime * 100);
            int pipeIndex = 2*(t1-t2)+5;
            
            String cellContent = sheetPipe.getRow(0).getCell(1).getStringCellValue();

            String id = "";

            if (cellContent != null && cellContent.length() > 0)
            {
                // get which pipe this is
                for (int cc = 4; Character.isDigit(cellContent.charAt(cc)); cc++)
                {
                    id += cellContent.charAt(cc);

                }

                if (!"".equals(id))
                {
                    // set the pipe id now
                    waterData.setId(Long.parseLong(id));
                }

                waterData.setType("Pipe" + id);

                // set the temperatures along the pipe
                for(int kk = 0; kk < 11; kk++)
                {
                    waterData.setTemp(sheetPipe.getRow(pipeIndex).getCell(2 + kk).getNumericCellValue(), kk);
                }

                waterData.setTime(getTimeAtIndex(index));

                list.add(waterData);
                                int row = pipeIndex;
                System.out.println("Pipe Data @ Row " + row + "     " + waterData); 
            }
        }
        else
        {
            
        }
        return list;
    }
    
    // set the stream data at the given index in the provided list
    List<WaterData> setStreamData(int index, List<WaterData> list)
    {        
        // process stream data
        while ((stopColumn < sheetStream.getRow(START_ROW_HEADER).getPhysicalNumberOfCells()))
        {
            String cellContent = sheetStream.getRow(START_ROW_HEADER).getCell(START_COLUMN_HEADER + COLUMN_OFFSET * stopColumn).getStringCellValue();
            int clen = cellContent.length();

            // if the pseudo header cell has an something in it with an s then we found a stream set
            if (clen > 0 && cellContent.contains("s")){
                // get new water data
                WaterData waterData = new WaterData();

                waterData.setType("Stream" + cellContent);
                waterData.setId(Long.parseLong(cellContent.substring(1,clen)));
                //System.out.println("TIME S" + sheetStream.getRow(START_ROW_DATA + stopRow).getCell(TIME_COLUMN + COLUMN_OFFSET * stopColumn).getNumericCellValue());
                waterData.setTemp(sheetStream.getRow(START_ROW_DATA + index).getCell(TEMP_COLUMN + COLUMN_OFFSET * stopColumn).getNumericCellValue(), 0);
                waterData.setTime(sheetStream.getRow(START_ROW_DATA + index).getCell(TIME_COLUMN + COLUMN_OFFSET * stopColumn).getNumericCellValue());
                list.add(waterData);
                int row = START_ROW_DATA + index;
                System.out.println("Stream Data @ Row " + row + "     " + waterData); 

            }
            stopColumn++;
        }

        if (stopColumn >= sheetStream.getRow(1).getPhysicalNumberOfCells())
        {
            stopColumn = 0;
        }

        return list;
    }

    
    @Override
    public double getStartTime()
    {
        return 0.0;
    }
    
    @Override
    public double getEndTime()
    {
        return endTime;
    }
    
    @Override
    public void shutdown()
    {
        
    }
    
    
}
