package bayes_network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import bayes_network.cpd.CPDTree;
import bayes_network.cpd.CPDTreeBuilder;

import data.DataSet;
import data.attribute.Attribute;
import data.attribute.AttributeSet;
import directed_acyclic.DetectCycles;
import directed_acyclic.TopologicalSort;

/**
 * Manages the nodes and all node and edge operations in a Bayesian network
 * such as adding/removing nodes and edges and updating the parameters in the
 * model.
 * 
 * @author Matthew Bernstein - matthewb@cs.wisc.edu
 *
 */
public class BNNodeManager 
{
    /**
     * The attributes represented by nodes
     */
    private AttributeSet attributes;
    
    /**
     * A mapping of attributes to the nodes representing these attributes
     */
    private Map<Attribute, BNNode> nodeMap;
    
    /**
     * A sorted list of the nodes in the network.  Sorted topologically.
     */
    private ArrayList<BNNode> nodeList;
    
    /**
     * Constructor
     */
    public BNNodeManager()
    {
        attributes = new AttributeSet();
        nodeMap = new HashMap<Attribute, BNNode>();
        nodeList = new ArrayList<BNNode>();
    }
    
    /**
     * Get a node by the attribute it represents
     * 
     * @param attr the attribute the target node represents
     * @return the node
     */
    public BNNode getNode(Attribute attr)
    {   
        return nodeMap.get(attr);
    }
    
    /**
     * @return the attributes represented by nodes in the network
     */
    public AttributeSet getAttributeSet()
    {
        return this.attributes;
    }
    
    /**
     * @return a list of all attributes that are represented by nodes in the
     * network
     */
    public ArrayList<Attribute> getAttributes()
    {
        return this.attributes.getAttributes();
    }
    
    /**
     * @return the nodes in the network sorted topologically
     */
    public ArrayList<BNNode> topologicallySorted()
    {
        return nodeList;
    }
    
    /**
     * Tests if a hypothetical edge between two nodes is valid.  That is, 
     * whether this edge would create a cycle in the network. This method
     * does not create the edge in the network.
     * 
     * @param parent the parent node
     * @param child the child node
     * @return true if the edge is valid (i.e. no cycle).  False otherwise
     */
    public Boolean isValidEdge(BNNode parent, BNNode child)
    {
        /*
         * Add edge
         */
        parent.addChild(child);
        child.addParent(parent);
        
        /*
         * Check for cycle
         */
        Double[][] graph = BNUtility.convertToAdjacencyMatrix(nodeList);
        Boolean cycle = DetectCycles.run(graph);
        
        /*
         * Remove edge
         */
        child.removeParent(parent);
        parent.removeChild(child);
        
        return !cycle;
    }

    /**
     * Determines if reversing the edge from (parent -> child) will result in a 
     * valid DAG.  If the original edge (parent -> child) doesn't exists, then
     * this method simply is testing whether adding (child -> parent) is valid.
     * 
     * @param parent the parent node
     * @param child the child node
     * @return true if reversing the edge will not result in a cycle, false
     * otherwise
     */
    public Boolean isValidReverseEdge(BNNode parent, BNNode child)
    {
        Boolean result;
        
        if (!edgeExists(parent, child))
        {
            result = isValidEdge(child, parent);
        }
        else
        {
            /*
             * Remove parent -> child
             */
            parent.removeChild(child);
            child.removeParent(parent);
            
            /*
             * Create child -> parent
             */
            child.addChild(parent);
            parent.addParent(child);
            
            /*
             * Check for cycle
             */
            Double[][] graph = BNUtility.convertToAdjacencyMatrix(nodeList);
            result = !DetectCycles.run(graph);
            
            /*
             * Remove child -> parent
             */
            child.removeChild(parent);
            parent.removeParent(parent);
            
            /*
             * Restore parent -> child
             */
            parent.addChild(child);
            child.addParent(parent);
        }
        
        return result;
    }
    
    /**
     * Test if an edge exists in the network
     * 
     * @param parent the parent node
     * @param child the child node
     * @return true if the edge exists, false otherwise
     */
    public Boolean edgeExists(BNNode parent, BNNode child)
    {
        boolean childFound = false;
        boolean parentFound = false;
        
        /*
         *  Search for child
         */
        if (parent.getChildren().contains(child))
        {
            childFound = true;
        }
        
        /*
         *  Search for parent
         */
        if (child.getParents().contains(parent))
        {
            parentFound = true;
        }
        
        return childFound && parentFound;
    }
    
    /**
     * Create a directed edge between two nodes
     * 
     * @param parent the parent node
     * @param child the child node
     */
    public void createEdge(BNNode parent, 
                           BNNode child, 
                           DataSet data, 
                           Integer laplaceCount)
    {
        /*
         * Check if the edge is valid 
         */
        if (!isValidEdge(parent, child))
        {
            System.err.println("Error adding edge from node " + 
                    parent.getName() + " to node " + 
                    child.getName() + ". This creates a cycle."); 
            return;
        }
        
        /*
         * Check if edge already exists 
         */
        if (edgeExists(parent, child))
        {
            System.err.println("Error adding edge from node " + 
                                parent.getName() + " to node " +
                                child.getName() + ". This edge already exists");
            return;
        }
        
        parent.addChild(child);
        child.addParent(parent);
                
        /*
         *  Rebuild the child's CPD 
         */
        buildCPD( child, data, laplaceCount );
        
        /*
         * Resort the nodes topologically 
         */
        topologicalSort();
    }
    
    /**
     * Remove a directed edge between two nodes
     * 
     * @param parent the parent node
     * @param child the child node
     */
    public void removeEdge(BNNode parent, 
                            BNNode child, 
                            DataSet data, 
                            Integer laplaceCount)
    {
        parent.removeChild(child);
        child.removeParent(parent);
        
        /*
         *  Rebuild the child's CPD
         */
        buildCPD( child, data, laplaceCount );
        
        /*
         *  Resort the nodes topologically
         */
        topologicalSort();
    }
    
    /**
     * Reverse a directed edge between two nodes
     * 
     * @param parent the parent node
     * @param child the child node
     */
    public void reverseEdge(BNNode parent, 
                           BNNode child, 
                           DataSet data, 
                           Integer laplaceCount)
    {
        parent.removeChild(child);
        child.removeParent(parent);
        
        child.addChild(parent);
        parent.addParent(child);
                
        /*
         *  Rebuild the child's CPD
         */
        buildCPD( child, data, laplaceCount );
        buildCPD( parent, data, laplaceCount );
        
        /*
         *  Resort the nodes topologically
         */
        topologicalSort();
    }
    
    /**
     * Add a node to the network and sort the all nodes topologically
     * 
     * @param newNode the new node
     */
    public void addNode(BNNode newNode, DataSet data, Integer laplaceCount)
    {
        attributes.addAttribute(newNode.getAttribute());
        nodeMap.put(newNode.getAttribute(), newNode);
        nodeList.add(newNode);
        
        /*
         * Resort the nodes topologically
         */
        topologicalSort();
        
        buildCPD( newNode, data, laplaceCount);
    }
    
    /**
     * @return the number of nodes
     */
    public Integer getNumNodes()
    {
        return this.nodeList.size();
    }
    
    /**
     * Build the CPD Tree for a single node
     * 
     * @param node the node for which we need to build the CPD tree
     * @param data the data used to build the CPD
     */
    public void buildCPD(BNNode node, DataSet data, Integer laplaceCount)
    { 
        System.out.println("BUILDING CPD");
        
        ArrayList<Attribute> cpdAttributes = new ArrayList<Attribute>();

        /*
         *  Get parent node's associated attribute
         */
        for (BNNode parent : node.getParents())
        {
            cpdAttributes.add(parent.getAttribute());
        }

        /*
         *  Add the current node's attribute
         */
        cpdAttributes.add(node.getAttribute());

        /*
         *  Build the CPD at this node
         */
        CPDTreeBuilder treeBuilder = new CPDTreeBuilder();
        CPDTree cpdTree = treeBuilder.buildCPDTree(data, 
                                                   cpdAttributes,
                                                   laplaceCount);
        
        /*
         *  Set the CPD Tree
         */
        node.setCPDTree( cpdTree );   
    }
    
    /**
     * Sort the nodes topologically
     */
    private void topologicalSort()
    {
        Double[][] graph = BNUtility.convertToAdjacencyMatrix(this.nodeList);
        ArrayList<Integer> sortedIndices = TopologicalSort.run(graph);
        
        //TODO REMOVE!
        System.out.println("TOPOLOGICAL SORT");
        
        ArrayList<BNNode> newSorted = new ArrayList<BNNode>();
        for (Integer index : sortedIndices)
        {
            // TODO REMOVE!
            System.out.println(nodeList.get(index).getName());
            newSorted.add(nodeList.get(index));
        }
        
        nodeList = newSorted;
    }
    
}
