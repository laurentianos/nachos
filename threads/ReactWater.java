package nachos.threads;

import nachos.machine.*;

public class ReactWater{
	
	Semaphore h;
	Semaphore o;
	
	public ReactWater(){
		
		h = new Semaphore(0);
		o = new Semaphore(0);
	}
	
	
	public void hReady(){
		
		h.V();
	}
	
	public void oReady(){
		o.V();
	}
	
	//TODO NEED TO LOOK AT Semaphore.java --> line 93
	public void makeWater(){
		
		for(int i = 0; i < 3;i++){
			if(i <= 1)
				hReady();
			else
				oReady();
		}
		
		h.P();
		h.P();
		o.P();
		System.out.println("We have Water");
		
	}
} 

