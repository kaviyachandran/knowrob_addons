/*
 * ROSClient_low_level.java
 * Copyright (c) 2013 Asil Kaan Bozcuoglu, 2015 Daniel Beßler
 *
 * All rights reserved.
 *
 * Software License Agreement (BSD License)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Universitaet Bremen nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.knowrob.cram;

import geometry_msgs.PoseStamped;

import java.util.*;
import java.util.Map.*;
import java.lang.Integer;

import javax.vecmath.Matrix4d;

import org.knowrob.interfaces.mongo.*;
import org.knowrob.interfaces.mongo.types.*;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;

import javax.vecmath.Vector3d;

import designator_integration_msgs.KeyValuePair;


public class LogdataPublisher extends AbstractNodeMain {
	MongoDBInterface mdb;

	ConnectedNode node;
	Publisher<designator_integration_msgs.Designator> pub;
	Publisher<std_msgs.String> pub_image;

	@Override
	public GraphName getDefaultNodeName() {
		return GraphName.of("knowrob_log_publisher");
	}

	@Override
	public void onStart(final ConnectedNode connectedNode) {
		node = connectedNode;
		mdb = new MongoDBInterface();

		pub = connectedNode.newPublisher("logged_designators", designator_integration_msgs.Designator._TYPE);
		pub_image = connectedNode.newPublisher("logged_images", std_msgs.String._TYPE); 
	}
	
	public boolean waitOnPublisher() {
		try {
			while(pub_image ==null) {
				Thread.sleep(200);
			}
			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public boolean publishImage(String image_path) {
		// wait for publisher to be ready
		if(!waitOnPublisher()) return false;

		// create image message from file
		final std_msgs.String image_msg = pub_image.newMessage();

		image_msg.setData(image_path);
		pub_image.publish(image_msg);
		return true;
	}

	public boolean publishDesignator(String designatorId) {
		final StringTokenizer s1 = new StringTokenizer(designatorId, "#");
		s1.nextToken();
		designatorId= s1.nextToken();

		org.knowrob.interfaces.mongo.types.Designator d1 = mdb.getDesignatorByID(designatorId);
		publishDesignator(d1);
		return true;
	}

	public boolean publishDesignator(org.knowrob.interfaces.mongo.types.Designator designator) {
		// wait for publisher to be ready
		if(!waitOnPublisher()) return false;

		final designator_integration_msgs.Designator designator_msg = pub.newMessage();

		try {
			if(designator.getType().toString().toLowerCase() == "action")
				designator_msg.setType(1);
			else if(designator.getType().toString().toLowerCase() == "object")
				designator_msg.setType(2);
			else if(designator.getType().toString().toLowerCase() == "location")
				designator_msg.setType(0);			
		} catch (java.lang.NullPointerException exc) {
			designator_msg.setType(0);
		}

		publishDesignator(designator, designator_msg, 0);

		return true;
	}

	public int publishDesignator(Designator designator, designator_integration_msgs.Designator designator_msg, int parentId) {

		Set<Entry<String, Object>> values = designator.entrySet();
		Object[] pairs = values.toArray();

		int difference_in_x = 0;
		for(int x = parentId; x < pairs.length + parentId + difference_in_x; x++)
		{
			@SuppressWarnings("unchecked")
			Entry<String, Object> currentEntry = (Entry<String, Object>) pairs[x - parentId - difference_in_x];
			String key = currentEntry.getKey();
			
			// check if publishable key
			if(key.substring(0,1).equals("_")) continue;

			KeyValuePair k1 = node.getTopicMessageFactory().newFromType(designator_integration_msgs.KeyValuePair._TYPE);
			designator_msg.getDescription().add(k1);

			int c = designator_msg.getDescription().size()-1;

			designator_msg.getDescription().get(c).setId(x + 1);
			designator_msg.getDescription().get(c).setKey(key);
			if(parentId == 0)
			{
				designator_msg.getDescription().get(c).setParent(parentId);
			}
			else 
			{
				designator_msg.getDescription().get(c).setParent(parentId +1);
			}


			if (currentEntry.getValue() instanceof Double) 
			{
				designator_msg.getDescription().get(c).setType(1);
				Double current_value = (Double)currentEntry.getValue();
				designator_msg.getDescription().get(c).setValueFloat((float)current_value.doubleValue());						
			}
			else if (currentEntry.getValue() instanceof Integer) 
			{
				designator_msg.getDescription().get(c).setType(1);
				Double current_value = (Double)currentEntry.getValue();
				designator_msg.getDescription().get(c).setValueFloat((float)current_value.doubleValue());
			}
			else if (currentEntry.getValue() instanceof ISODate) 
			{
				designator_msg.getDescription().get(c).setType(0);
				ISODate date = (ISODate)currentEntry.getValue();
				designator_msg.getDescription().get(c).setValueString(date.toString());
			}
			else if (currentEntry.getValue() instanceof Designator) 
			{
				Designator inner_designator = (Designator)currentEntry.getValue();					
				try
				{
					if(inner_designator.getType().toString().toLowerCase() == "action")
						designator_msg.getDescription().get(c).setType(6);
					else if(inner_designator.getType().toString().toLowerCase() == "object")
						designator_msg.getDescription().get(c).setType(7);
					else if(inner_designator.getType().toString().toLowerCase() == "location")
						designator_msg.getDescription().get(c).setType(8);				
				}
				catch (java.lang.NullPointerException exc)
				{
					designator_msg.getDescription().get(c).setType(6);
				}
				int inner_size = publishDesignator(inner_designator, designator_msg, x);
				x += inner_size;
				difference_in_x += inner_size;
			}
			else if (currentEntry.getValue() instanceof PoseStamped) 
			{
				designator_msg.getDescription().get(c).setType(4);
				PoseStamped pose = (PoseStamped) currentEntry.getValue();
				designator_msg.getDescription().get(c).setValuePosestamped(pose);
			}
			else if (currentEntry.getValue() instanceof geometry_msgs.Pose) 
			{
				designator_msg.getDescription().get(c).setType(5);
				geometry_msgs.Pose pose = (geometry_msgs.Pose) currentEntry.getValue();
				designator_msg.getDescription().get(c).setValuePose(pose);
			}
			else if (currentEntry.getValue() instanceof Matrix4d)
			{
				handleMatrixValue(designator_msg, c, (Matrix4d) currentEntry.getValue());
			}
			else if (currentEntry.getValue() instanceof Vector3d)
			{
				handleVectorValue(designator_msg, c, (Vector3d) currentEntry.getValue());
			}
			else if (currentEntry.getValue() instanceof tfjava.Stamped<?>)
			{
				Object innerVal = ((tfjava.Stamped<?>)currentEntry.getValue()).getData();
				if (innerVal instanceof Matrix4d)
					handleMatrixValue(designator_msg, c, (Matrix4d) innerVal);
				else if (innerVal instanceof Vector3d)
					handleVectorValue(designator_msg, c, (Vector3d) innerVal);
			}
			else if (currentEntry.getValue().getClass().equals(String.class)) 
			{
				designator_msg.getDescription().get(c).setType(0);
				designator_msg.getDescription().get(c).setValueString((String)currentEntry.getValue());
			}
		}
		
		if(parentId == 0)
		{
			pub.publish(designator_msg);
		}
		
		return pairs.length + difference_in_x;
	}

	private void handleVectorValue(designator_integration_msgs.Designator designator_msg, int c, Vector3d vec) {
		double val[] = new double[3];
		vec.get(val);
		designator_msg.getDescription().get(c).setType(13);
		designator_msg.getDescription().get(c).setValueArray(val);
	}

	private void handleMatrixValue(designator_integration_msgs.Designator designator_msg, int c, Matrix4d mat) {
		double val[] = {
			mat.m00, mat.m01, mat.m02, mat.m03,
			mat.m10, mat.m11, mat.m12, mat.m13,
			mat.m20, mat.m21, mat.m22, mat.m23,
			mat.m30, mat.m31, mat.m32, mat.m33
		};
		designator_msg.getDescription().get(c).setType(12);
		designator_msg.getDescription().get(c).setValueArray(val);
	}

}
