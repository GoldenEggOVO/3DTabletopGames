package dev.tabletop3d.rules;

import java.util.*;

/** Color Eight rules. The lastcard identifier remains stable for table configuration. */
public final class LastCardGame implements HandGame {
    private static final List<String> COLORS=List.of("r","y","b","p");
    private static final List<Piece> CARDS=cards();
    private final List<List<Integer>> hands=new ArrayList<>();
    private final List<Integer> deck=new ArrayList<>(),pile=new ArrayList<>(),placements=new ArrayList<>();
    private final SplittableRandom random;
    private int current,direction=1,activeRank,pendingColor=-1;
    private boolean drawn;
    private String activeColor="",result="ongoing",lastAction="Ready to play";

    public LastCardGame(int players,long seed){
        if(players<2||players>5)throw new IllegalArgumentException("Color Eight requires 2 to 5 players");
        random=new SplittableRandom(seed);
        for(int card=0;card<CARDS.size();card++)deck.add(card);shuffle();
        for(int seat=0;seat<players;seat++)hands.add(new ArrayList<>());
        for(int card=0;card<5;card++)for(int seat=0;seat<players;seat++)drawCards(seat,1);
        // Opening special cards establish matching only; wilds and swaps cannot open.
        int index=deck.size()-1;while(deck.get(index)>=48)index--;
        int opening=deck.remove(index);pile.add(opening);activeColor=COLORS.get(opening/12);activeRank=rank(opening);
    }
    @Override public String id(){return "lastcard";}
    @Override public int playerCount(){return hands.size();}
    @Override public int currentPlayer(){return current;}
    @Override public boolean finished(){return !result.equals("ongoing");}
    @Override public String outcome(){return result;}
    public List<Integer> placements(){return List.copyOf(placements);}
    public String pendingCard(int seat){return seat==current&&pendingColor>=0?String.valueOf(pendingColor):null;}
    public String timeoutAction(){
        if(pendingColor<0)return "pass";
        String color=COLORS.stream().max(Comparator.comparingLong(c->hand(current).stream().filter(p->p.face().startsWith(c)).count())).orElse("r");
        return "play:"+pendingColor+":"+color;
    }
    @Override public List<Piece> hand(int seat){return hands.get(seat).stream().map(CARDS::get).toList();}
    @Override public List<Piece> discards(int seat){return List.of();}
    @Override public List<Piece> exposed(int seat){return List.of();}
    @Override public int handSize(int seat){return hands.get(seat).size();}
    @Override public int deckSize(){return deck.size();}
    @Override public List<Cell> cells(){
        int top=pile.getLast();String face=top>=48&&top<52?activeColor+"8":CARDS.get(top).face();
        return List.of(new Cell("deck",0,0,"Deck "+deck.size(),-1),new Cell("discard",2,0,face,-1));
    }
    @Override public List<String> legalActions(int seat){
        if(finished()||seat!=current)return List.of();List<String> actions=new ArrayList<>();
        if(pendingColor>=0){for(String color:COLORS)actions.add("play:"+pendingColor+":"+color);return List.copyOf(actions);}
        for(int card:hands.get(seat))if(playable(card)){
            if(card>=48&&card<52){for(String color:COLORS)actions.add("play:"+card+":"+color);actions.add("choose:"+card);}
            else actions.add("play:"+card);
        }
        if(!drawn)actions.add("draw");actions.add("pass");return List.copyOf(actions);
    }
    @Override public List<String> actionsForCell(int seat,String id){return "deck".equals(id)&&legalActions(seat).contains("draw")?List.of("draw"):List.of();}
    private int matchingRank(int rank){return playerCount()==2&&rank==11?10:rank;}
    private boolean playable(int card){return card>=48||COLORS.get(card/12).equals(activeColor)||matchingRank(rank(card))==matchingRank(activeRank);}
    @Override public void apply(int seat,String action){
        if(action==null||!legalActions(seat).contains(action))throw new IllegalArgumentException("Invalid Color Eight action");
        if(action.startsWith("choose:")){pendingColor=Integer.parseInt(action.substring(7));return;}
        if(action.equals("draw")||action.equals("pass")){
            int count=drawn?0:drawCards(seat,1);drawn=true;lastAction="Player "+(seat+1)+" drew "+count+" cards";
            if(action.equals("pass")||hands.get(seat).stream().noneMatch(this::playable))advance(1);
            return;
        }
        String[] parts=action.split(":");int card=Integer.parseInt(parts[1]),rank=rank(card);pendingColor=-1;
        hands.get(seat).remove(Integer.valueOf(card));pile.add(card);
        if(card<52){activeColor=card>=48?parts[2]:COLORS.get(card/12);activeRank=rank;}
        lastAction="Player "+(seat+1)+" played "+(card>=48&&card<52?activeColor+"8":CARDS.get(card).face());
        if(handSize(seat)==0){placements.add(seat);result="winner:"+seat;return;}
        if(rank==9){for(int other=0;other<playerCount();other++)if(other!=seat)drawCards(other,1);}
        if(card>=52){
            int other=Math.floorMod(current+direction,playerCount());
            List<Integer> ours=hands.get(seat);hands.set(seat,hands.get(other));hands.set(other,ours);
        }
        if(rank==11&&playerCount()>2)direction=-direction;
        advance(rank==10||rank==11&&playerCount()==2?2:1);
    }
    private void advance(int count){current=Math.floorMod(current+direction*count,playerCount());drawn=false;pendingColor=-1;}
    private int drawCards(int seat,int count){
        int drawn=0;
        while(drawn<count){
            if(deck.isEmpty()){
                if(pile.size()<2)break;
                int top=pile.removeLast();deck.addAll(pile);pile.clear();pile.add(top);shuffle();
            }
            hands.get(seat).add(deck.removeLast());drawn++;
        }
        return drawn;
    }
    private void shuffle(){for(int i=deck.size()-1;i>0;i--)Collections.swap(deck,i,random.nextInt(i+1));}
    private static int rank(int card){return card>=52?13:card>=48?12:card%12;}
    private static List<Piece> cards(){
        List<Piece> cards=new ArrayList<>();List<String> ranks=List.of("1","2","3","4","5","6","7","9","10","Draw1","Skip","Reverse");
        for(String color:COLORS)for(String rank:ranks)cards.add(new Piece(String.valueOf(cards.size()),color+rank));
        for(int i=0;i<4;i++)cards.add(new Piece(String.valueOf(cards.size()),"wild"));
        for(int i=0;i<2;i++)cards.add(new Piece(String.valueOf(cards.size()),"swap"));return List.copyOf(cards);
    }
    @Override public Map<String,String> publicInfo(){
        return Map.of("rules","Match color or rank; wild eight chooses color; draw one, skip, reverse or swap; first empty hand wins",
            "rulesVariant","color-eight-54-v1","phase",finished()?"Finished":pendingColor>=0?"Choose color":"Play a card",
            "turn","Player "+(current+1),"color",activeColor,"direction",direction==1?"Clockwise":"Counterclockwise",
            "drawn",String.valueOf(drawn),"handSizes",hands.stream().map(List::size).toList().toString(),"placements",placements.toString(),"lastAction",lastAction);
    }
}
