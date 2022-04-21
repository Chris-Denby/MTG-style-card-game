/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Interface;

import Interface.Cards.*;
import Interface.Constants.CardLocation;
import Interface.Constants.CreatureEffect;
import Interface.Constants.TurnPhase;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.util.Timer;
import java.util.function.BiConsumer;
import javax.swing.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author chris
 */
public class PlayArea extends JPanel
{
    GameWindow gameWindow;
    Card artifactInPlay = null;
    boolean isOpponent = false;
    int width;
    int height;
    private boolean isPlayerTurn;
    //JPanel cardSubPanel;
    JPanel playerSubPanel;
    PlayerBox playerBox;
    CardSubPanelWithSlots cardSubPanel;


    private LinkedHashMap<Integer,Card> cardsInPlay = new LinkedHashMap<Integer,Card>();
    private ArrayList<Card> cardList;
    private Deque<CardEvent> cardEventStack = new ArrayDeque<CardEvent>();
    ArrayList<Card> discardPile = new ArrayList<Card>();
 
    public PlayArea(int containerWidth, int containerHeight, GameWindow window, boolean isOpponent)
    {
        gameWindow = window;
        this.isOpponent = isOpponent;
        width = containerWidth;
        this.setOpaque(false);
        //height is the container minus the who player and opponents hands
        height = (int) containerHeight-Math.round((containerHeight/16)*3); 
        this.setPreferredSize(new Dimension(width,height));
        this.setOpaque(false);


        
        //cardSubPanel = new JPanel();
        cardSubPanel = new CardSubPanelWithSlots();
        //cardSubPanel.setOpaque(false);
        Dimension cardSubPanelDimension = new Dimension(width,Math.round(height/10)*6);
        cardSubPanel.setPreferredSize(cardSubPanelDimension);
        cardSubPanel.setSize(cardSubPanelDimension);
        
        playerSubPanel = new JPanel();
        playerSubPanel.setOpaque(false);
        Dimension playerSubPanelDimension = new Dimension(width,Math.round(height/10)*4);
        playerSubPanel.setPreferredSize(playerSubPanelDimension);
        playerSubPanel.setSize(playerSubPanelDimension);
                
        this.setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        
        if(!isOpponent)
        {
            this.add(cardSubPanel);
            this.add(playerSubPanel);
        }
        else
        {
            this.add(playerSubPanel); 
            this.add(cardSubPanel);
        }   
        
        playerBox = new PlayerBox(playerSubPanel.getHeight(),this.isOpponent, this);
        playerBox.setImage(gameWindow.getImageFromCache(ThreadLocalRandom.current().nextInt(1001,1003)));
        
        playerBox.addMouseListener(new PlayerBoxMouseListener(playerBox,this));
        playerSubPanel.add(playerBox,Component.CENTER_ALIGNMENT);

        this.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) 
            {
                /**
                 * DISABLED MANUAL DRAW - draw happens automatically at start of turn
                if(gameWindow.getIsPlayerTurn())
                {
                    drawCard();
                }
                **/
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if(gameWindow.getIsPlayerTurn())
                    gameWindow.cancelCardEvent();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        });
    }
    
    public GameWindow getGameWindow()
    {
        return gameWindow;
    }
    
    public int getNumCardsInPlayArea()
    {
        return cardsInPlay.size();
    }
    
    public PlayerBox getPlayerBoxPanel()
    {
        return playerBox;
    }
    
    public void setIsPlayerTurn(boolean is)
    {
        isPlayerTurn = is;
        if(isPlayerTurn)
            unActivateAllCards();
    }

    public boolean addCard(Card card)
    {
        if(cardSubPanel.checkIfFull())
            return false;

        gameWindow.playSound("playCard");
        //set card location
        if(isOpponent)
            card.setCardLocation(CardLocation.OPPONENT_PLAY_AREA);
        else
            card.setCardLocation(CardLocation.PLAYER_PLAY_AREA);

        card.setPlayArea(this);
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setAlignmentY(Component.CENTER_ALIGNMENT);
        card.setFaceUp(true);

        //Determine which segment of hte play area to add the card to
        //based on card type
        if(card instanceof BannerCard){
            if(artifactInPlay!=null)
            {
                this.removeCard(artifactInPlay);
            }
            artifactInPlay = card;
            playerSubPanel.add(card);
        }
        else{
            cardSubPanel.addCard(card);
        }

        //remove mouse listener assigned when card was added to players hand
        card.removeMouseListener(card.getMouseListeners()[0]);
        //add new mouse listener relevant to the play area
        card.addMouseListener(new CardMouseListener(card,this));

        cardsInPlay.put(card.getCardID(), card);
        triggerETBEffect(card);

        if(card instanceof SpellCard)
        {
            SpellCard scard = (SpellCard) card;

            //do activate on enter the battlefield
            Timer timer = new Timer();
            TimerTask tt = new TimerTask() {
                @Override
                public void run()
                {
                    timer.cancel();
                    selectCard(scard);
                    gameWindow.playSound("playSpell");
                }
            };
            timer.schedule(tt, 500);
        }
        return true;
    }
    
    public void removeCard(Card card)
    {
        if(cardsInPlay.containsKey(card.getCardID()))
        {
            playerSubPanel.remove(cardsInPlay.get(card.getCardID()));
            cardSubPanel.removeCard(cardsInPlay.get(card.getCardID()));
            addToDiscardPile(card);
            cardsInPlay.remove(card.getCardID()); 
            revalidate();
            repaint();
        }
    }
    
    public void addToDiscardPile(Card card)
    {
        discardPile.add(card);
    }
        
    public void selectCard(Card card)
    {
        gameWindow.playSound("selectCard");
        gameWindow.createCardEvent(card);   
    }
    
    public HashMap<Integer,Card> getCardsInPlayArea()
    {
        return cardsInPlay;
    }
    
    public void triggerETBEffect(Card card)
    {
        if(card.getCreatureEffect()==null)
            return;

        //String effectName = card.getETBeffect().toString().split("_")[0];
        switch(card.getCreatureEffect())
        {
            case Taunt:
                //this is a passive ability
                //taunt works by the attacking player checking if taunt creature is in play
                //with this classes checkForTauntCreature() method
                //card.showTauntSymbol();
            break;
                
            case Buff_Power:
                gameWindow.playSound("buffSound");

                cardList = new ArrayList<Card>(cardsInPlay.values());
                int buffValue = Math.round(card.getPlayCost()/Constants.buffModifier);
                if(buffValue<1)
                    buffValue = 1;

                for(int x=0; x<cardList.size();x++)
                {       
                    int playedCardIndex = cardList.indexOf(card);
                    Card c = cardList.get(x);
                    //buff only creatures <buff distance> to the left
                    if(x>=(playedCardIndex-Constants.buffDistance) && c.getCardID()!=card.getCardID() && c instanceof CreatureCard)
                    {
                        CreatureCard ccard = (CreatureCard) c;
                        ccard.setBuffed(buffValue);
                        gameWindow.componentAnimateMap.put(c,"slash");
                    }
                }
                gameWindow.drawAnimations();
            break;
            
            case Stealth:
                if(isOpponent)
                    card.setFaceUp(false);
            break;
        }        
    }
    
    public void triggerDeathFffect(Card card)
    {        
        //remove ETB buffs or trigger death effects
        if(card.getCreatureEffect()!=null)
        {
            //String ETBeffectName = card.getETBeffect().toString().split("_")[0];
            switch(card.getCreatureEffect())
            {
                case Gain_Life:
                    gameWindow.playSound("gainLife");
                    this.playerBox.gainLife(card.getPlayCost());
                break;
                
                case Taunt:
                break;

                case Buff_Power:
                    gameWindow.playSound("debuffSound");
                    cardList = new ArrayList<Card>(cardsInPlay.values());
                    int buffValue = Math.round(card.getPlayCost()/Constants.buffModifier);
                    if(buffValue<1)
                        buffValue = 1;
                    buffValue = buffValue*-1;


                    for(Card c:cardsInPlay.values())
                    {  
                        int x = cardList.indexOf(c);
                        int indexOfBuffer = cardList.indexOf(card);
                        
                        if(x>=(indexOfBuffer-Constants.buffDistance) &&c.getCardID()!=card.getCardID() && c instanceof CreatureCard){
                            CreatureCard ccard = (CreatureCard) c;
                            if(ccard.getIsBuffed())
                                ccard.setBuffed(buffValue);
                            }
                    }
                break;  
            } 
        }
    }
        
    public class CardMouseListener implements MouseListener
    {
        private Container container;
        private Card card;
        
        public CardMouseListener(Card card, Container container)
        {
            this.card = card;
            this.container = container;
        }

        @Override
        public void mouseClicked(MouseEvent e) 
        {

        }

        @Override
        public void mousePressed(MouseEvent e) 
        {
        }

        @Override
        public void mouseReleased(MouseEvent e) {

            System.out.println(card.getWidth() +", " + card.getHeight());
            //only allow mouse events while its the players turn, or if its the declare blockers phase
            if(e.getButton()==MouseEvent.BUTTON1 && gameWindow.getTurnPhase()==TurnPhase.END_PHASE)
                return;
                       
            if(e.getButton()==MouseEvent.BUTTON1 && !gameWindow.getIsPlayerTurn() && gameWindow.getTurnPhase()==TurnPhase.DECLARE_BLOCKERS && !card.getIsActivated() && card.getCardLocation()==CardLocation.PLAYER_PLAY_AREA)
            {
                //if mouse 1 clicked
                //and is not your turn
                //and its declare blockers turn phase
                //and card is not already activated
                //add the clicked card is in your play area
                selectCard(card);
            }
            else
            if(e.getButton()==MouseEvent.BUTTON1 && gameWindow.getIsPlayerTurn() && gameWindow.getTurnPhase()!=TurnPhase.DECLARE_BLOCKERS)
            {
                //if mouse 1 clicked
                //and it is your turn
                //and the turn phase is NOT declare blockers
                selectCard(card);    
            }
            else
            if(e.getButton()==MouseEvent.BUTTON1 && gameWindow.getIsPlayerTurn() && gameWindow.getTurnPhase()==TurnPhase.COMBAT_PHASE)
            {
                //if mouse 1 clicked
                //and it is your turn
                //and its combat phase
                selectCard(card);
                
            }
            else
            if(e.getButton()==MouseEvent.BUTTON3)
            {
                //if mouse 3 (right button) clicked
                //gameWindow.zoomInCard(card);
                //card.getCardSize();
            }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }
        
    }
    
    public class PlayerBoxMouseListener implements MouseListener
    {
        private Container container;
        private PlayerBox playerBox;
        
        public PlayerBoxMouseListener(PlayerBox box, Container container)
        {
            this.playerBox = box;
            this.container = container;
        }
        
        
        @Override
        public void mouseClicked(MouseEvent e) 
        {

        }

        @Override
        public void mousePressed(MouseEvent e) 
        {
        }

        @Override
        public void mouseReleased(MouseEvent e) { 
            //only allow mouse events while its the players
            System.out.println(playerBox.getWidth() + ", " + playerBox.getHeight());
            if(e.getButton()==MouseEvent.BUTTON1)
            {
                if(gameWindow.getIsPlayerTurn())
                {
                    gameWindow.createCardEvent(playerBox);           
                }               
            }
            if(e.getButton()==MouseEvent.BUTTON3)
            {
            }

        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }
        
    }
    
    public void unActivateAllCards()
    {
        BiConsumer <Integer,Card> consumer = (i,card)->{card.setIsActivated(false);};
        cardsInPlay.forEach(consumer);
        
    }
    
    public boolean checkForAvailableBlockers()
    {
        //returns true if any cards in play area are available to block
        //else returns false
        for(Map.Entry<Integer,Card> entry:cardsInPlay.entrySet())
        {
            if(entry.getValue() instanceof CreatureCard)
            {
                CreatureCard c = (CreatureCard) entry.getValue();
                if(!c.getIsActivated())
                    return true;
                else
                    return false;             
            }      
        }
        return false;        
    } 
    
    public boolean checkForTauntCreature()
    {
        cardList = new ArrayList<Card>(cardsInPlay.values());
        
        for(Card c:cardList)
        {
            //for each creature card player has in play
            //if a creature without taunt is present, mark it as not attackable
            if(c instanceof CreatureCard && ((CreatureCard )c).getCreatureEffect()==CreatureEffect.Taunt)
                return true;
        }
        return false;
    }

    private class CardSubPanelWithSlots extends JPanel
    {
        GridLayout gridLayout = new GridLayout(1,3,30,30);
        JPanel innerPanel = new JPanel();
        JPanel slot1PlaceholderPanel = new JPanel();
        JPanel slot2PlaceholderPanel = new JPanel();
        JPanel slot3PlaceholderPanel = new JPanel();
        JPanel[] cardSlots = new JPanel[Constants.numCardSlots];

        public CardSubPanelWithSlots()
        {
            //this.setOpaque(false);
            this.setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
            Dimension cardSubPanelDimension = new Dimension(width,Math.round(height/10)*6);
            this.setPreferredSize(cardSubPanelDimension);
            this.setSize(cardSubPanelDimension);

            slot1PlaceholderPanel.setBackground(Constants.shadowColor);
            slot2PlaceholderPanel.setBackground(Constants.shadowColor);
            slot3PlaceholderPanel.setBackground(Constants.shadowColor);

            innerPanel.setLayout(gridLayout);
            innerPanel.add(slot1PlaceholderPanel);
            innerPanel.add(slot2PlaceholderPanel);
            innerPanel.add(slot3PlaceholderPanel);

            cardSlots[0] = slot1PlaceholderPanel;
            cardSlots[1] = slot2PlaceholderPanel;
            cardSlots[2] = slot3PlaceholderPanel;

            this.add(innerPanel);
        }

        public boolean checkIfFull()
        {
            //if last slot is filled, all slots are taken
            if(cardSlots[Constants.numCardSlots-1].getComponentCount()>0)
                return true;
            else
                return false;
        }

        public void addCard(Card card)
        {
            //add the card to the left most free slot
            for(int x=0;x<Constants.numCardSlots;x++)
            {
                if(cardSlots[x].getComponentCount()==0){
                    cardSlots[x].add(card);
                    return;
                }
            }
        }

        public void removeCard(Card card)
        {
            for(int x=0;x<cardSlots.length-1;x++)
            {
                if(cardSlots[x].getComponentCount()>0 && ((Card) cardSlots[x].getComponent(0)).getCardID()==card.getCardID())
                {
                    cardSlots[x].removeAll();
                }
            }
        }

    }
    
    

    
    
    
    
}