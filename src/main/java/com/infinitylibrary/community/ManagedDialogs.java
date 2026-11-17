package com.infinitylibrary.community;

import com.infinitylibrary.InfinityLibraryPlugin;
import io.papermc.paper.dialog.*;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** One-use, owner-bound dialogs. Every mutating callback still revalidates its feature ACL/revision. */
public final class ManagedDialogs {
    public record Button(String label,Consumer<DialogResponseView> action){}
    private final InfinityLibraryPlugin plugin;
    private final Map<UUID,UUID> tokens=new HashMap<>();
    public ManagedDialogs(InfinityLibraryPlugin plugin){this.plugin=plugin;}
    public void invalidate(Player p){tokens.remove(p.getUniqueId());}
    public void clear(){tokens.clear();}
    public void form(Player p,String title,List<DialogInput> fields,Consumer<DialogResponseView> save,Runnable cancel){show(p,title,"",fields,List.of(new Button("Save",save),new Button("Cancel",r -> cancel.run())));}
    public void show(Player p,String title,String body,List<DialogInput> fields,List<Button> buttons){
        UUID owner=p.getUniqueId(),token=UUID.randomUUID();tokens.put(owner,token);p.closeInventory();List<ActionButton> actions=new ArrayList<>();
        for(Button button:buttons)actions.add(ActionButton.create(Component.text(button.label()),null,145,DialogAction.customClick((response,audience) -> {
            if(!(audience instanceof Player actor)||!actor.getUniqueId().equals(owner))return;
            Bukkit.getScheduler().runTask(plugin,()->{if(!p.isOnline()||!p.hasPermission("infinitylibrary.command")||!token.equals(tokens.get(owner)))return;tokens.remove(owner);try{button.action().accept(response);}catch(RuntimeException ex){p.sendMessage(Component.text(ex.getMessage()==null?"Unable to perform this action.":ex.getMessage()));}});
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build())));
        Dialog dialog=Dialog.create(factory -> factory.empty().base(DialogBase.builder(Component.text(title)).canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE).body(body.isEmpty()?List.of():List.of(DialogBody.plainMessage(Component.text(body),300))).inputs(fields).build()).type(DialogType.multiAction(actions,null,2)));
        p.showDialog(dialog);
    }
}
